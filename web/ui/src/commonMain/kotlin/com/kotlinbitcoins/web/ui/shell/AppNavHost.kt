package com.kotlinbitcoins.web.ui.shell

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.session.LoginBanner
import com.kotlinbitcoins.web.state.session.SessionEvent
import com.kotlinbitcoins.web.state.session.SessionGate
import com.kotlinbitcoins.web.state.session.SessionManager
import com.kotlinbitcoins.web.state.session.SessionState
import com.kotlinbitcoins.web.ui.components.LoadingIndicator
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.page_title_format
import com.kotlinbitcoins.web.ui.theme.LocalReducedMotion
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource

/**
 * Navigation of the app (W-04): one destination per [AppRoute], guarded by [SessionGate] on every destination
 * change, on every session state change and on every [SessionEvent]; `document.title` per destination; focus on the
 * h1 of each page (PageHeading). Destinations of user stories not implemented yet show a placeholder.
 *
 * Screens receive their ViewModels from [viewModels], built by :app; :ui never knows :data.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    startRoute: AppRoute,
    sessionManager: SessionManager,
    ports: ShellPorts,
    viewModels: AppViewModels,
    modifier: Modifier = Modifier,
) {
    val sessionState by sessionManager.state.collectAsState()
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.toAppRoute()
    val loginBanner = remember { mutableStateOf<LoginBanner?>(null) }

    // Guard on destination and session changes: a route the gate does not allow is replaced in the history.
    // The live entry: a session event may have navigated since this composition (e.g. logout → login).
    LaunchedEffect(currentRoute, sessionState) {
        val route = navController.currentBackStackEntry?.toAppRoute() ?: return@LaunchedEffect
        val allowed = SessionGate.resolve(route, sessionState)
        if (allowed != null && allowed != route) navController.replace(allowed)
    }

    // Session events: expiry and logout clear the back stack, so "back" never shows protected data (US1-7).
    // The live entry (not the last composed one): the guard above may have navigated already.
    LaunchedEffect(sessionManager) {
        sessionManager.events.collect { event ->
            val current = navController.currentBackStackEntry?.toAppRoute() ?: return@collect
            val redirect = SessionGate.onEvent(event, current) ?: return@collect
            loginBanner.value = redirect.banner
            if (event == SessionEvent.LoggedIn) {
                navController.replace(redirect.route)
            } else {
                navController.navigate(redirect.route) { popUpTo(navController.graph.id) { inclusive = true } }
            }
        }
    }

    val title = currentRoute?.let { stringResource(Res.string.page_title_format, it.pageName()) }
    LaunchedEffect(title) { title?.let(ports.documentTitle::set) }

    val reducedMotion = LocalReducedMotion.current
    Surface(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startRoute,
            enterTransition = { if (reducedMotion) EnterTransition.None else fadeIn() },
            exitTransition = { if (reducedMotion) ExitTransition.None else fadeOut() },
            popEnterTransition = { if (reducedMotion) EnterTransition.None else fadeIn() },
            popExitTransition = { if (reducedMotion) ExitTransition.None else fadeOut() },
        ) {
            accountDestinations(
                sessionState = sessionManager.state,
                viewModels = viewModels,
                navigator = AccountNavigator(navController, ports.announcer, loginBanner),
            )

            val shell = Shell(navController, onLogout = sessionManager::logout)
            protectedDestination<AppRoute.Dashboard>(sessionManager.state, shell) {
                DashboardDestination(viewModels, onNavigate = shell::navigate)
            }
            protectedDestination<AppRoute.Deposit>(sessionManager.state, shell) {
                DepositDestination(viewModels, ports, onNavigate = shell::navigate)
            }
            protectedDestination<AppRoute.Purchase>(sessionManager.state, shell) { PlaceholderScreen(it.pageName()) }
            protectedDestination<AppRoute.Investments>(sessionManager.state, shell) { PlaceholderScreen(it.pageName()) }
            protectedDestination<AppRoute.Redemption>(sessionManager.state, shell) { PlaceholderScreen(it.pageName()) }
            protectedDestination<AppRoute.Statement>(sessionManager.state, shell) { PlaceholderScreen(it.pageName()) }
            protectedDestination<AppRoute.Market>(sessionManager.state, shell) { PlaceholderScreen(it.pageName()) }
        }
    }
}

/** Navigation callbacks of the adaptive scaffold. */
internal class Shell(
    val navController: NavHostController,
    val onLogout: () -> Unit,
) {
    fun navigate(route: AppRoute) {
        navController.navigate(route) { launchSingleTop = true }
    }
}

/** Replaces the current entry (no new history entry): gate redirects and login → return destination. */
internal fun NavHostController.replace(route: AppRoute) {
    if (currentBackStackEntry?.toAppRoute() == route) return
    val currentId = currentBackStackEntry?.destination?.id
    navigate(route) {
        if (currentId != null) popUpTo(currentId) { inclusive = true }
        launchSingleTop = true
    }
}

// The graph is built once, so destinations receive the session as a flow, never as a captured value.
internal inline fun <reified R : AppRoute> NavGraphBuilder.publicDestination(
    sessionState: StateFlow<SessionState>,
    crossinline content: @Composable (R) -> Unit,
) {
    composable<R> { entry ->
        val route = entry.toAppRoute() as R
        Guarded(route, sessionState) { content(route) }
    }
}

private inline fun <reified R : AppRoute> NavGraphBuilder.protectedDestination(
    sessionState: StateFlow<SessionState>,
    shell: Shell,
    crossinline content: @Composable (R) -> Unit,
) {
    composable<R> { entry ->
        val route = entry.toAppRoute() as R
        Guarded(route, sessionState) {
            AdaptiveScaffold(current = route, onNavigate = shell::navigate, onLogout = shell.onLogout) {
                content(route)
            }
        }
    }
}

/**
 * Composes the page only when the gate allows exactly this route; otherwise (resolving, or about to redirect)
 * only "Carregando…" exists, so no protected content ever reaches the accessibility mirror.
 */
@Composable
internal fun Guarded(
    route: AppRoute,
    sessionState: StateFlow<SessionState>,
    content: @Composable () -> Unit,
) {
    val state by sessionState.collectAsState()
    if (SessionGate.resolve(route, state) == route) {
        content()
    } else {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
            LoadingIndicator()
        }
    }
}

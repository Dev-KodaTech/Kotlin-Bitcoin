@file:OptIn(ExperimentalWasmJsInterop::class)

package com.kotlinbitcoins.web.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavHostController
import androidx.navigation.bindToBrowserNavigation
import androidx.navigation.compose.rememberNavController
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.ui.components.LocalFieldAccessibility
import com.kotlinbitcoins.web.ui.shell.AppNavHost
import com.kotlinbitcoins.web.ui.shell.ShellPorts
import com.kotlinbitcoins.web.ui.shell.toAppRoute
import com.kotlinbitcoins.web.ui.theme.AppTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow

/** Lets the browser binding handle back/forward first; a hash still different after that came from a link (L9). */
private const val HASH_CHANGE_SETTLE_MS = 50L

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("UNUSED_EXPRESSION")
fun main() {
    // Mandatory reference: keeps the named time zone data (America/Sao_Paulo) in the bundle (W-07).
    JsJodaTimeZoneModule
    installComposeA11yShim()

    val container = AppContainer()
    container.sessionManager.start()
    onPageUnload(container::close)
    val startRoute = AppRoute.parse(locationHash())

    ComposeViewport(viewportContainerId = "compose-root") {
        val navController = rememberNavController()
        BrowserNavigation(navController)
        LaunchedEffect(Unit) {
            withFrameNanos { }
            removeBootScreen()
        }
        CompositionLocalProvider(LocalFieldAccessibility provides DomFieldAccessibility) {
            AppTheme(container.userPreferences) {
                AppNavHost(
                    navController = navController,
                    startRoute = startRoute,
                    sessionManager = container.sessionManager,
                    ports = ShellPorts(container.documentTitle, container.announcer),
                    viewModels = container.viewModels,
                )
            }
        }
    }
}

/**
 * The route lives in the URL fragment (W-04): `bindToBrowserNavigation` writes it and restores it on back/forward
 * and reload. A fragment changed inside the same document (a `#/…` link or the address bar) is not handled by the
 * binding (L9 of W-00), so `hashchange` navigates when the fragment still differs from the current route.
 */
@OptIn(ExperimentalBrowserHistoryApi::class)
@Composable
private fun BrowserNavigation(navController: NavHostController) {
    LaunchedEffect(navController) {
        navController.bindToBrowserNavigation { entry -> entry.toAppRoute()?.toFragment() ?: "" }
    }
    LaunchedEffect(navController) {
        val changes = Channel<Unit>(Channel.CONFLATED)
        onHashChange { changes.trySend(Unit) }
        changes.receiveAsFlow().collect {
            delay(HASH_CHANGE_SETTLE_MS)
            val target = AppRoute.parse(locationHash())
            if (target != navController.currentBackStackEntry?.toAppRoute()) {
                navController.navigate(target) { launchSingleTop = true }
            }
        }
    }
}

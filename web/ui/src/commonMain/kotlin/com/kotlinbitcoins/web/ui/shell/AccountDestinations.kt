package com.kotlinbitcoins.web.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.account.AccountNavigation
import com.kotlinbitcoins.web.state.port.Announcer
import com.kotlinbitcoins.web.state.session.LoginBanner
import com.kotlinbitcoins.web.state.session.SessionState
import com.kotlinbitcoins.web.ui.account.LoginActions
import com.kotlinbitcoins.web.ui.account.LoginScreen
import com.kotlinbitcoins.web.ui.account.RegisterActions
import com.kotlinbitcoins.web.ui.account.RegisterScreen
import kotlinx.coroutines.flow.StateFlow

/** Login and registration (US1): public destinations with their ViewModels. */
internal fun NavGraphBuilder.accountDestinations(
    sessionState: StateFlow<SessionState>,
    viewModels: AppViewModels,
    navigator: AccountNavigator,
) {
    publicDestination<AppRoute.Login>(sessionState) { route ->
        // The banner of the navigation is read once, when the page (and its ViewModel) is created. It is not
        // cleared here: after an expiry the guard may open a login page just before the event sets the banner.
        val initialBanner = remember { navigator.pendingLoginBanner }
        val viewModel = viewModel { viewModels.login(route.returnTo, initialBanner) }
        val state by viewModel.uiState.collectAsState()
        NavigateAfterSubmit(state.navigation, navigator, viewModel::onNavigationHandled)
        LoginScreen(
            state = state,
            actions =
                LoginActions(
                    onEmailChange = viewModel::onEmailChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onSubmit = viewModel::onSubmit,
                    onGoToRegister = navigator::goToRegister,
                    announce = navigator::announce,
                ),
        )
    }
    publicDestination<AppRoute.Register>(sessionState) {
        val viewModel = viewModel { viewModels.register() }
        val state by viewModel.uiState.collectAsState()
        NavigateAfterSubmit(state.navigation, navigator, viewModel::onNavigationHandled)
        RegisterScreen(
            state = state,
            actions =
                RegisterActions(
                    onNameChange = viewModel::onNameChange,
                    onEmailChange = viewModel::onEmailChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onSubmit = viewModel::onSubmit,
                    onFocusHandled = viewModel::onFocusHandled,
                    onGoToLogin = navigator::goToLogin,
                    announce = navigator::announce,
                ),
        )
    }
}

/**
 * Applies the result of a submit only while this page is the current entry: a page that is leaving (replaced by the
 * session guard, still composed during its exit transition) must not navigate over the page that replaced it.
 */
@Composable
private fun NavigateAfterSubmit(
    navigation: AccountNavigation?,
    navigator: AccountNavigator,
    onHandled: () -> Unit,
) {
    val entry = LocalViewModelStoreOwner.current
    LaunchedEffect(navigation) {
        navigation ?: return@LaunchedEffect
        navigator.afterSubmit(navigation, from = entry)
        onHandled()
    }
}

/**
 * Navigation of the public pages. [loginBanner] carries the message of the next login page (session expired,
 * account created); the page takes it when it is created. It goes back to null on login, logout and the "Entrar"
 * link, so an old message never reappears.
 */
internal class AccountNavigator(
    private val navController: NavHostController,
    private val announcer: Announcer,
    private val loginBanner: MutableState<LoginBanner?>,
) {
    val pendingLoginBanner: LoginBanner? get() = loginBanner.value

    fun goToLogin() {
        loginBanner.value = null
        navController.navigate(AppRoute.Login()) { launchSingleTop = true }
    }

    fun goToRegister() {
        navController.navigate(AppRoute.Register) { launchSingleTop = true }
    }

    /**
     * Result of a submit of the page [from]: replaces its entry. After a login, the session guard usually got there
     * first (the page leaves as soon as the session is active), so this is a no-op then.
     */
    fun afterSubmit(
        navigation: AccountNavigation,
        from: ViewModelStoreOwner?,
    ) {
        if (from == null || from !== navController.currentBackStackEntry) return
        loginBanner.value = navigation.banner
        navController.replace(navigation.route)
    }

    fun announce(text: String) {
        announcer.assertive(text)
    }
}

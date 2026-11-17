@file:OptIn(ExperimentalWasmJsInterop::class)

package com.kotlinbitcoins.web.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.ui.components.LocalFieldAccessibility
import com.kotlinbitcoins.web.ui.shell.AppNavHost
import com.kotlinbitcoins.web.ui.shell.ShellPorts
import com.kotlinbitcoins.web.ui.theme.AppTheme

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

/** The route lives in the URL fragment (W-04), kept in sync both ways by [HashRouter]. */
@Composable
private fun BrowserNavigation(navController: NavHostController) {
    val router = remember(navController) { HashRouter(navController) }
    LaunchedEffect(router) { router.syncUrl() }
    LaunchedEffect(router) { router.followBrowser() }
}

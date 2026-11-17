package com.kotlinbitcoins.web.app

import androidx.navigation.NavHostController
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.core.navigation.BrowserChange
import com.kotlinbitcoins.web.core.navigation.HistoryAction
import com.kotlinbitcoins.web.core.navigation.HistoryEntry
import com.kotlinbitcoins.web.core.navigation.HistorySync
import com.kotlinbitcoins.web.ui.shell.toAppRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The router of the app over the URL fragment (research W-04, replacing the Experimental `bindToBrowserNavigation`,
 * which pushed a history entry even when the back stack was replaced). The decisions live in [HistorySync] (:core):
 * - back stack → URL ([syncUrl]): a new entry on top is `pushState`; any other change is `replaceState`;
 * - URL → back stack ([followBrowser]): back/forward, `#/…` links and the address bar fire `hashchange` (pushState
 *   and replaceState never do); the entry below is popped, any other route is navigated to, and that answer only
 *   replaces the URL, since the browser already shows it.
 */
internal class HashRouter(
    private val navController: NavHostController,
) {
    private var shown: List<HistoryEntry> = emptyList()
    private var answeringBrowser = false

    suspend fun syncUrl() {
        navController.currentBackStack.collect { stack ->
            val next = stack.mapNotNull { entry -> entry.toAppRoute()?.let { HistoryEntry(entry.id, it.toFragment()) } }
            if (next.isEmpty()) return@collect
            when (val action = HistorySync.action(shown, next, answeringBrowser)) {
                is HistoryAction.Push -> historyPush(action.fragment)
                is HistoryAction.Replace -> historyReplace(action.fragment)
                HistoryAction.None -> Unit
            }
            shown = next
            answeringBrowser = false
        }
    }

    suspend fun followBrowser() {
        val changes = Channel<Unit>(Channel.CONFLATED)
        onHashChange { changes.trySend(Unit) }
        changes.receiveAsFlow().collect {
            val stack = navController.currentBackStack.value.mapNotNull { it.toAppRoute() }
            when (val change = HistorySync.onBrowserChange(stack, AppRoute.parse(locationHash()))) {
                BrowserChange.None -> {
                    Unit
                }

                BrowserChange.Pop -> {
                    answeringBrowser = true
                    navController.popBackStack()
                }

                is BrowserChange.Navigate -> {
                    answeringBrowser = true
                    navController.navigate(change.route) { launchSingleTop = true }
                }
            }
        }
    }
}

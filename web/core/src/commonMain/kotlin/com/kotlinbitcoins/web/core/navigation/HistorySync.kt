package com.kotlinbitcoins.web.core.navigation

/** One entry of the back stack of the app as the URL sees it: its stable id and its fragment ("#/painel"). */
data class HistoryEntry(
    val id: String,
    val fragment: String,
)

/** What the browser history must do after the back stack of the app changed. */
sealed interface HistoryAction {
    data class Push(
        val fragment: String,
    ) : HistoryAction

    data class Replace(
        val fragment: String,
    ) : HistoryAction

    data object None : HistoryAction
}

/** What the app must do after the fragment changed in the browser (back/forward, a `#/…` link, the address bar). */
sealed interface BrowserChange {
    data object None : BrowserChange

    /** The target is the entry below the current one: the app goes back too. */
    data object Pop : BrowserChange

    data class Navigate(
        val route: AppRoute,
    ) : BrowserChange
}

/**
 * The router of the app over the URL fragment (research W-04, fallback of `bindToBrowserNavigation`): only a new
 * entry on top of the back stack adds a history entry; every other change (gate redirects, login → destination,
 * logout, a filter applied) **replaces** the current one (contracts/ui-routes.md, "Histórico do navegador").
 */
object HistorySync {
    /** [fromBrowser]: the change answers a fragment the browser already shows, so nothing is added to the history. */
    fun action(
        previous: List<HistoryEntry>,
        next: List<HistoryEntry>,
        fromBrowser: Boolean,
    ): HistoryAction {
        val top = next.lastOrNull() ?: return HistoryAction.None
        val previousIds = previous.map { it.id }
        val nextIds = next.map { it.id }
        return when {
            fromBrowser -> {
                HistoryAction.Replace(top.fragment)
            }

            nextIds == previousIds -> {
                HistoryAction.None
            }

            previous.isNotEmpty() && nextIds.size == previousIds.size + 1 && nextIds.dropLast(1) == previousIds -> {
                HistoryAction.Push(top.fragment)
            }

            else -> {
                HistoryAction.Replace(top.fragment)
            }
        }
    }

    /** [stack] is the back stack of the app (bottom first); [target] the route of the fragment now in the URL. */
    fun onBrowserChange(
        stack: List<AppRoute>,
        target: AppRoute,
    ): BrowserChange =
        when {
            stack.lastOrNull() == target -> BrowserChange.None
            stack.size >= 2 && stack[stack.size - 2] == target -> BrowserChange.Pop
            else -> BrowserChange.Navigate(target)
        }
}

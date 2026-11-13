package com.kotlinbitcoins.web.app

import com.kotlinbitcoins.web.data.browser.BroadcastTabChannel
import com.kotlinbitcoins.web.data.browser.BrowserDocumentTitle
import com.kotlinbitcoins.web.data.browser.BrowserSessionStorage
import com.kotlinbitcoins.web.data.browser.DocumentVisibility
import com.kotlinbitcoins.web.data.browser.DomAnnouncer
import com.kotlinbitcoins.web.data.browser.MediaPreferences
import com.kotlinbitcoins.web.data.http.ApiCall
import com.kotlinbitcoins.web.data.http.HttpClientFactory
import com.kotlinbitcoins.web.data.time.SystemClock
import com.kotlinbitcoins.web.state.port.Announcer
import com.kotlinbitcoins.web.state.port.DocumentTitle
import com.kotlinbitcoins.web.state.port.PageVisibility
import com.kotlinbitcoins.web.state.port.UserPreferences
import com.kotlinbitcoins.web.state.session.SessionManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/**
 * Manual composition of the app (W-02, Principle I): the only place that knows the concrete implementations of the
 * ports. Gateways and ViewModel factories are added here by each user story.
 */
class AppContainer {
    private val scope = MainScope()
    private val tabChannel = BroadcastTabChannel()

    val sessionManager = SessionManager(BrowserSessionStorage(), tabChannel, SystemClock, scope)

    /** Same origin: the client's nginx proxies /api/ to the API (W-15), so no CORS and no URL in the bundle. */
    val apiCall =
        ApiCall(
            HttpClientFactory.create(baseUrl = locationOrigin() + API_PATH, tokenProvider = sessionManager),
            sessionManager,
        )

    val announcer: Announcer = DomAnnouncer()
    val pageVisibility: PageVisibility = DocumentVisibility()
    val userPreferences: UserPreferences = MediaPreferences()
    val documentTitle: DocumentTitle = BrowserDocumentTitle()

    fun close() {
        tabChannel.close()
        scope.cancel()
    }

    private companion object {
        const val API_PATH = "/api/v1/"
    }
}

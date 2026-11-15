package com.kotlinbitcoins.web.app

import com.kotlinbitcoins.web.data.browser.BroadcastTabChannel
import com.kotlinbitcoins.web.data.browser.BrowserDocumentTitle
import com.kotlinbitcoins.web.data.browser.BrowserSessionStorage
import com.kotlinbitcoins.web.data.browser.DocumentVisibility
import com.kotlinbitcoins.web.data.browser.DomAnnouncer
import com.kotlinbitcoins.web.data.browser.MediaPreferences
import com.kotlinbitcoins.web.data.gateway.HttpAccountGateway
import com.kotlinbitcoins.web.data.gateway.HttpAuthGateway
import com.kotlinbitcoins.web.data.gateway.HttpInvestmentGateway
import com.kotlinbitcoins.web.data.gateway.HttpProfileGateway
import com.kotlinbitcoins.web.data.gateway.HttpQuoteGateway
import com.kotlinbitcoins.web.data.http.ApiCall
import com.kotlinbitcoins.web.data.http.HttpClientFactory
import com.kotlinbitcoins.web.data.time.SystemClock
import com.kotlinbitcoins.web.state.account.LoginViewModel
import com.kotlinbitcoins.web.state.account.RegisterViewModel
import com.kotlinbitcoins.web.state.dashboard.DashboardViewModel
import com.kotlinbitcoins.web.state.deposit.DepositViewModel
import com.kotlinbitcoins.web.state.port.AccountGateway
import com.kotlinbitcoins.web.state.port.Announcer
import com.kotlinbitcoins.web.state.port.AuthGateway
import com.kotlinbitcoins.web.state.port.DocumentTitle
import com.kotlinbitcoins.web.state.port.InvestmentGateway
import com.kotlinbitcoins.web.state.port.PageVisibility
import com.kotlinbitcoins.web.state.port.ProfileGateway
import com.kotlinbitcoins.web.state.port.QuoteGateway
import com.kotlinbitcoins.web.state.port.UserPreferences
import com.kotlinbitcoins.web.state.purchase.PurchaseViewModel
import com.kotlinbitcoins.web.state.session.SessionManager
import com.kotlinbitcoins.web.ui.shell.AppViewModels
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

    private val authGateway: AuthGateway = HttpAuthGateway(apiCall)
    private val profileGateway: ProfileGateway = HttpProfileGateway(apiCall)
    private val accountGateway: AccountGateway = HttpAccountGateway(apiCall)
    private val quoteGateway: QuoteGateway = HttpQuoteGateway(apiCall)
    private val investmentGateway: InvestmentGateway = HttpInvestmentGateway(apiCall)

    val viewModels =
        AppViewModels(
            register = { RegisterViewModel(authGateway, sessionManager) },
            login = { returnTo, banner -> LoginViewModel(authGateway, sessionManager, returnTo, banner) },
            dashboard = { texts ->
                DashboardViewModel(
                    profileGateway = profileGateway,
                    accountGateway = accountGateway,
                    quoteGateway = quoteGateway,
                    investmentGateway = investmentGateway,
                    pageVisibility = pageVisibility,
                    announcer = announcer,
                    texts = texts,
                )
            },
            deposit = { texts -> DepositViewModel(accountGateway, announcer, texts) },
            purchase = { texts ->
                PurchaseViewModel(
                    accountGateway = accountGateway,
                    quoteGateway = quoteGateway,
                    investmentGateway = investmentGateway,
                    pageVisibility = pageVisibility,
                    announcer = announcer,
                    texts = texts,
                )
            },
        )

    fun close() {
        tabChannel.close()
        scope.cancel()
    }

    private companion object {
        const val API_PATH = "/api/v1/"
    }
}

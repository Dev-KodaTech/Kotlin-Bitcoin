package com.kotlinbitcoins.web.e2e.security

import com.kotlinbitcoins.web.e2e.auth.logout
import com.kotlinbitcoins.web.e2e.auth.storedToken
import com.kotlinbitcoins.web.e2e.operations.confirmButton
import com.kotlinbitcoins.web.e2e.operations.depositButton
import com.kotlinbitcoins.web.e2e.operations.fundedAccount
import com.kotlinbitcoins.web.e2e.operations.openDeposit
import com.kotlinbitcoins.web.e2e.operations.reviewPurchase
import com.kotlinbitcoins.web.e2e.operations.typeAmount
import com.kotlinbitcoins.web.e2e.operations.waitForDepositResult
import com.kotlinbitcoins.web.e2e.operations.waitForPurchaseResult
import com.kotlinbitcoins.web.e2e.support.E2eBase
import com.kotlinbitcoins.web.e2e.support.Screens
import com.kotlinbitcoins.web.e2e.support.goTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * T221 (FR-008): during a complete flow (login, deposit, purchase, investments, statement, market and logout) the
 * access token never appears in a URL (requests and navigations, fragment included), the console, `localStorage`,
 * a cookie or a `Referer`. It lives only in `sessionStorage` and in the `Authorization` header of the API requests.
 */
class TokenExposureE2ETest : E2eBase() {
    private data class SeenRequest(
        val url: String,
        val headers: Map<String, String>,
        val body: String?,
    )

    @ParameterizedTest
    @MethodSource("browsers")
    fun `the token stays in sessionStorage and in the Authorization header only`(browserName: String) {
        val email = api.fundedAccount("1000.00")
        val requests = mutableListOf<SeenRequest>()
        val navigations = mutableListOf<String>()
        val console = mutableListOf<String>()
        withPage(browserName) {
            page.onRequest { requests += SeenRequest(it.url(), it.allHeaders(), it.postData()) }
            page.onFrameNavigated { navigations += it.url() }
            page.onConsoleMessage { console += it.text() }

            openDeposit(email)
            val token = checkNotNull(storedToken()) { "no session after the login" }
            typeAmount("50")
            ui.click(depositButton())
            waitForDepositResult("R$ 50,00")

            goTo(Screens.PURCHASE)
            reviewPurchase("10")
            ui.click(confirmButton())
            waitForPurchaseResult()
            goTo(Screens.INVESTMENTS)
            goTo(Screens.STATEMENT)
            goTo(Screens.MARKET)
            navigations += page.url()

            val local = page.evaluate("() => JSON.stringify(Object.assign({}, localStorage))") as String
            val session = page.evaluate("() => JSON.stringify(Object.assign({}, sessionStorage))") as String
            val cookies = page.context().cookies().joinToString { "${it.name}=${it.value}" }
            val documentCookie = page.evaluate("() => document.cookie") as String

            assertThat(session).describedAs("sessionStorage").contains(token)
            assertThat(local).describedAs("localStorage").doesNotContain(token)
            assertThat(cookies + documentCookie).describedAs("cookies").doesNotContain(token)
            assertThat(navigations).describedAs("navigated URLs (fragment included)").noneMatch { token in it }
            assertThat(requests.map { it.url }).describedAs("request URLs").noneMatch { token in it }
            assertThat(requests.mapNotNull { it.headers["referer"] }).describedAs("Referer").noneMatch { token in it }
            assertThat(requests.mapNotNull { it.body }).describedAs("request bodies").noneMatch { token in it }
            assertThat(console).describedAs("console messages").noneMatch { token in it }

            val apiRequests = requests.filter { "/api/v1/" in it.url }
            val authenticated =
                apiRequests.filterNot { it.url.endsWith("/auth/login") || it.url.endsWith("/accounts") }
            assertThat(authenticated).describedAs("authenticated API requests").isNotEmpty()
            assertThat(authenticated).allSatisfy {
                assertThat(it.headers["authorization"]).describedAs(it.url).isEqualTo("Bearer $token")
            }
            requests.filterNot { "/api/v1/" in it.url }.forEach {
                assertThat(it.headers["authorization"]).describedAs("Authorization outside the API: ${it.url}").isNull()
            }
            // Every other header of every request is free of the token.
            assertThat(requests.flatMap { r -> r.headers.filterKeys { it != "authorization" }.values })
                .describedAs("other request headers")
                .noneMatch { token in it }

            logout()
            assertThat(page.evaluate("() => JSON.stringify(Object.assign({}, sessionStorage))") as String)
                .describedAs("sessionStorage after Sair")
                .doesNotContain(token)
        }
    }
}

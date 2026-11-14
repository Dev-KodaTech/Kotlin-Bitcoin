package com.kotlinbitcoins.web.e2e.auth

import com.kotlinbitcoins.web.e2e.support.AppPage
import com.kotlinbitcoins.web.e2e.support.Axe
import com.kotlinbitcoins.web.e2e.support.E2eBase
import com.microsoft.playwright.Route
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * US1-6 and SC-009: the session ends at its expiry instant or on a 401, the login shows the expired message and,
 * after login, the same page (with its filter) opens again (T110). The fake clock of Playwright moves past
 * `expiresIn` (3600 s) without waiting.
 */
class SessionExpiryE2ETest : E2eBase() {
    private fun registered(): String = api.uniqueEmail().also { api.register("Ana", it, PASSWORD) }

    private fun AppPage.loginAt(
        fragment: String,
        email: String,
    ) {
        open(fragment)
        waitForLogin()
        login(email)
        ui.waitForHash(fragment)
    }

    private fun AppPage.expireAndWaitForLogin() {
        page.clock().fastForward(PAST_EXPIRY)
        waitForLogin()
        page.getByText(SESSION_EXPIRED).first().waitFor()
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `expiry on the statement with a filter returns to the same filter after login`(browserName: String) {
        val email = registered()
        withPage(browserName, prepare = { it.clock().install() }) {
            loginAt(STATEMENT_WITH_FILTER, email)
            ui.heading("Extrato").waitFor()

            expireAndWaitForLogin()

            assertThat(ui.hash()).isEqualTo("#/entrar?voltar=%2Fextrato%3Fde%3D2026-10-01%26ate%3D2026-11-09")
            waitForAnnouncement(SESSION_EXPIRED)
            assertThat(storedToken()).isNull()
            assertThat(headingCount("Extrato")).isZero()
            Axe.assertNoViolations(page)

            login(email)
            ui.waitForHash(STATEMENT_WITH_FILTER)
            ui.heading("Extrato").waitFor()
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `a 401 on a read ends the session like the expiry`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            open("#/entrar")
            waitForLogin()
            login(email)
            waitForDashboard()

            val unauthorized = ApiRequests(page, "/me/")
            page.route("**/api/v1/{me,quotes,market}/**") { route: Route ->
                route.fulfill(
                    Route
                        .FulfillOptions()
                        .setStatus(HTTP_UNAUTHORIZED)
                        .setContentType("application/problem+json")
                        .setBody(UNAUTHORIZED_PROBLEM),
                )
            }
            page.reload()
            waitForApp()
            page.waitForTimeout(READ_WAIT_MS)
            // The dashboard reads the API only from US2 on; before that there is no read to refuse.
            assumeTrue(unauthorized.count > 0, "the dashboard has no protected read yet (US2)")

            waitForLogin()
            page.getByText(SESSION_EXPIRED).first().waitFor()
            assertThat(ui.hash()).isEqualTo("#/entrar?voltar=%2Fpainel")

            page.unroute("**/api/v1/{me,quotes,market}/**")
            login(email)
            waitForDashboard()
        }
    }

    // Edge Case: expiry with a purchase being reviewed; the typed amount is never restored (FR-004).
    @ParameterizedTest
    @MethodSource("browsers")
    fun `expiry during a purchase returns to the purchase page with an empty form`(browserName: String) {
        val email = registered()
        withPage(browserName, prepare = { it.clock().install() }) {
            loginAt("#/comprar", email)
            ui.heading("Comprar bitcoin").waitFor()

            expireAndWaitForLogin()
            login(email)

            ui.waitForHash("#/comprar")
            ui.heading("Comprar bitcoin").waitFor()
            // The ViewModel of the purchase is created again: there is no review step with the old amount.
            assertThat(ui.button("Confirmar compra").count()).isZero()
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `expiry reaches the other tab`(browserName: String) {
        val email = registered()
        withPage(browserName, prepare = { it.clock().install() }) {
            loginAt("#/painel", email)
            val second = AppPage(page.context().newPage(), baseUrl)
            second.open("#/mercado")
            second.ui.heading("Mercado").waitFor()

            expireAndWaitForLogin()

            second.waitForLogin()
            second.page
                .getByText(SESSION_EXPIRED)
                .first()
                .waitFor()
            assertThat(second.ui.hash()).isEqualTo("#/entrar?voltar=%2Fmercado")
            assertThat(second.storedToken()).isNull()
        }
    }

    private companion object {
        const val STATEMENT_WITH_FILTER = "#/extrato?de=2026-10-01&ate=2026-11-09"

        /** `expiresIn` of the API (3600 s) plus a margin. */
        const val PAST_EXPIRY = "01:00:05"
        const val HTTP_UNAUTHORIZED = 401
        const val READ_WAIT_MS = 2_000.0
        const val UNAUTHORIZED_PROBLEM =
            """{"type":"about:blank","title":"Unauthorized","status":401,"code":"UNAUTHORIZED",""" +
                """"detail":"Token ausente, inválido ou expirado."}"""
    }
}

package com.kotlinbitcoins.web.e2e.operations

import com.kotlinbitcoins.web.e2e.auth.hasText
import com.kotlinbitcoins.web.e2e.support.Axe
import com.kotlinbitcoins.web.e2e.support.E2eBase
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Route
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * SC-004 and FR-043 (T145): a deposit is sent once whatever the person does while it is being sent (double click,
 * Enter five times, double tap, reload) or after its result (back/forward). The statement of the API is the judge:
 * exactly one deposit. FR-041: when the response is lost after the request reached the API, the result is uncertain
 * and no resend is offered.
 */
class DepositSubmissionSafetyE2ETest : E2eBase() {
    @ParameterizedTest
    @MethodSource("browsers")
    fun `double click sends one deposit`(browserName: String) {
        val email = api.registeredAccount()
        withPage(browserName) {
            openDeposit(email)
            typeAmount("10,00")

            depositButton().dblclick(Locator.DblclickOptions().setForce(true))

            waitForDepositResult("R$ 10,00")
        }
        assertThat(api.depositCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `Enter five times sends one deposit`(browserName: String) {
        val email = api.registeredAccount()
        withPage(browserName) {
            openDeposit(email)
            typeAmount("10,00")

            repeat(ENTER_PRESSES) { page.keyboard().press("Enter") }

            waitForDepositResult("R$ 10,00")
        }
        assertThat(api.depositCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `double tap sends one deposit`(browserName: String) {
        val email = api.registeredAccount()
        withPage(browserName, hasTouch = true) {
            openDeposit(email)
            typeAmount("10,00")

            val button = depositButton()
            button.tap(Locator.TapOptions().setForce(true))
            button.tap(Locator.TapOptions().setForce(true))

            waitForDepositResult("R$ 10,00")
        }
        assertThat(api.depositCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `reloading while the deposit is sent does not send it again`(browserName: String) {
        val email = api.registeredAccount()
        withPage(browserName) {
            openDeposit(email)
            // The request reaches the API, but its response is held: the page is still "sending" when it reloads.
            var reachedApi = false
            page.route(DEPOSITS_ROUTE) { route: Route ->
                route.fetch()
                reachedApi = true
            }
            typeAmount("10,00")
            ui.click(depositButton())
            page.waitForCondition { reachedApi }

            // No unroute: it would release the held request to the network again. The reload cancels it.
            page.reload()
            waitForApp()

            waitForDepositForm()
            assertThat(hasText("Depósito de R$ 10,00 realizado")).isFalse()
        }
        assertThat(api.depositCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `back and forward after the result do not send it again`(browserName: String) {
        val email = api.registeredAccount()
        withPage(browserName) {
            openDeposit(email)
            typeAmount("10,00")
            ui.click(depositButton())
            waitForDepositResult("R$ 10,00")

            page.goBack()
            ui.waitForHash("#/painel")
            page.goForward()
            waitForDepositForm()
            page.goBack()
            ui.waitForHash("#/painel")
        }
        assertThat(api.depositCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `a response lost after the API received the deposit is uncertain and offers no resend`(browserName: String) {
        val email = api.registeredAccount()
        withPage(browserName) {
            openDeposit(email)
            page.route(DEPOSITS_ROUTE) { route: Route ->
                route.fetch()
                route.abort()
            }
            typeAmount("10,00")
            ui.click(depositButton())

            page.getByText(UNCERTAIN).first().waitFor()
            ui.button("Ver saldo").waitFor()
            ui.button("Ver extrato").waitFor()
            assertThat(ui.button("Tentar de novo").count()).isZero()
            assertThat(
                depositButton().count(),
            ).describedAs("no submit button (the menu item is '…, página atual')").isZero()
            Axe.assertNoViolations(page)

            ui.click(ui.button("Ver saldo"))
            ui.waitForHash("#/painel")
            page.getByText("R$ 10,00").first().waitFor()
        }
        assertThat(api.depositCount(email)).isEqualTo(1)
    }

    private companion object {
        const val ENTER_PRESSES = 5
    }
}

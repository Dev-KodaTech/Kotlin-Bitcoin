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
 * US6-9, SC-004 and FR-043 (T179): a redemption is sent once whatever the person does while it is being sent (double
 * click, Enter five times, double tap, reload) or after its result (back/forward). The statement of the API is the
 * judge: exactly one redemption (one `requestId`). FR-041: a response lost after the request reached the API is
 * uncertain and offers no resend.
 */
class RedemptionSubmissionSafetyE2ETest : E2eBase() {
    @ParameterizedTest
    @MethodSource("browsers")
    fun `double click sends one redemption`(browserName: String) {
        val email = api.investedAccount("100.00", "100.00")
        withPage(browserName) {
            openRedemption(email)
            reviewRedemption("10,00")

            redemptionConfirmButton().dblclick(Locator.DblclickOptions().setForce(true))

            waitForRedemptionResult()
        }
        assertThat(api.redemptionCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `Enter five times sends one redemption`(browserName: String) {
        val email = api.investedAccount("100.00", "100.00")
        withPage(browserName) {
            openRedemption(email)
            reviewRedemption("10,00")

            tabToConfirmRedemption()
            repeat(ENTER_PRESSES) { page.keyboard().press("Enter") }

            waitForRedemptionResult()
        }
        assertThat(api.redemptionCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `double tap sends one redemption`(browserName: String) {
        val email = api.investedAccount("100.00", "100.00")
        withPage(browserName, hasTouch = true) {
            openRedemption(email)
            reviewRedemption("10,00")

            val button = redemptionConfirmButton()
            button.tap(Locator.TapOptions().setForce(true))
            button.tap(Locator.TapOptions().setForce(true))

            waitForRedemptionResult()
        }
        assertThat(api.redemptionCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `reloading while the redemption is sent does not send it again`(browserName: String) {
        val email = api.investedAccount("100.00", "100.00")
        withPage(browserName) {
            openRedemption(email)
            reviewRedemption("10,00")
            // The request reaches the API, but its response is held: the page is still "sending" when it reloads.
            var reachedApi = false
            page.route(REDEMPTIONS_ROUTE) { route: Route ->
                route.fetch()
                reachedApi = true
            }
            ui.click(redemptionConfirmButton())
            page.waitForCondition { reachedApi }

            // No unroute: it would release the held request to the network again. The reload cancels it.
            page.reload()
            waitForApp()

            waitForRedemptionForm()
            assertThat(hasText("Resgate realizado")).isFalse()
        }
        assertThat(api.redemptionCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `back and forward after the result do not send it again`(browserName: String) {
        val email = api.investedAccount("100.00", "100.00")
        withPage(browserName) {
            openRedemption(email)
            reviewRedemption("10,00")
            ui.click(redemptionConfirmButton())
            waitForRedemptionResult()

            page.goBack()
            ui.waitForHash("#/painel")
            page.goForward()
            waitForRedemptionForm()
            page.goBack()
            ui.waitForHash("#/painel")
        }
        assertThat(api.redemptionCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `a response lost after the API received the redemption is uncertain and offers no resend`(browserName: String) {
        val email = api.investedAccount("100.00", "100.00")
        withPage(browserName) {
            openRedemption(email)
            reviewRedemption("10,00")
            page.route(REDEMPTIONS_ROUTE) { route: Route ->
                route.fetch()
                route.abort()
            }

            ui.click(redemptionConfirmButton())

            page.getByText(UNCERTAIN).first().waitFor()
            ui.button("Ver saldo").waitFor()
            ui.button("Ver extrato").waitFor()
            assertThat(ui.button("Tentar de novo").count()).isZero()
            assertThat(redemptionConfirmButton().count()).isZero()
            assertThat(hasText("Estimativa")).isFalse()
            Axe.assertNoViolations(page)

            ui.click(ui.button("Ver saldo"))
            ui.waitForHash("#/painel")
            // The balance was R$ 0,00 after the purchase: the redemption of R$ 10,00 happened.
            page.getByText("R$ 10,00").first().waitFor()
        }
        assertThat(api.redemptionCount(email)).isEqualTo(1)
    }

    private companion object {
        const val ENTER_PRESSES = 5
    }
}

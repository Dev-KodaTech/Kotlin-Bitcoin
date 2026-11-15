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
 * US4-8, SC-004 and FR-043 (T158): a purchase is sent once whatever the person does while it is being sent (double
 * click, Enter five times, double tap, reload) or after its result (back/forward). The statement of the API is the
 * judge: exactly one purchase. FR-041: a response lost after the request reached the API is uncertain and offers no
 * resend.
 */
class PurchaseSubmissionSafetyE2ETest : E2eBase() {
    @ParameterizedTest
    @MethodSource("browsers")
    fun `double click sends one purchase`(browserName: String) {
        val email = api.fundedAccount("100.00")
        withPage(browserName) {
            openPurchase(email)
            reviewPurchase("10,00")

            confirmButton().dblclick(Locator.DblclickOptions().setForce(true))

            waitForPurchaseResult()
        }
        assertThat(api.purchaseCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `Enter five times sends one purchase`(browserName: String) {
        val email = api.fundedAccount("100.00")
        withPage(browserName) {
            openPurchase(email)
            reviewPurchase("10,00")

            tabToConfirm()
            repeat(ENTER_PRESSES) { page.keyboard().press("Enter") }

            waitForPurchaseResult()
        }
        assertThat(api.purchaseCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `double tap sends one purchase`(browserName: String) {
        val email = api.fundedAccount("100.00")
        withPage(browserName, hasTouch = true) {
            openPurchase(email)
            reviewPurchase("10,00")

            val button = confirmButton()
            button.tap(Locator.TapOptions().setForce(true))
            button.tap(Locator.TapOptions().setForce(true))

            waitForPurchaseResult()
        }
        assertThat(api.purchaseCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `reloading while the purchase is sent does not send it again`(browserName: String) {
        val email = api.fundedAccount("100.00")
        withPage(browserName) {
            openPurchase(email)
            reviewPurchase("10,00")
            // The request reaches the API, but its response is held: the page is still "sending" when it reloads.
            var reachedApi = false
            page.route(PURCHASES_ROUTE) { route: Route ->
                route.fetch()
                reachedApi = true
            }
            ui.click(confirmButton())
            page.waitForCondition { reachedApi }

            // No unroute: it would release the held request to the network again. The reload cancels it.
            page.reload()
            waitForApp()

            waitForPurchaseForm()
            assertThat(hasText("Compra realizada")).isFalse()
        }
        assertThat(api.purchaseCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `back and forward after the result do not send it again`(browserName: String) {
        val email = api.fundedAccount("100.00")
        withPage(browserName) {
            openPurchase(email)
            reviewPurchase("10,00")
            ui.click(confirmButton())
            waitForPurchaseResult()

            page.goBack()
            ui.waitForHash("#/painel")
            page.goForward()
            waitForPurchaseForm()
            page.goBack()
            ui.waitForHash("#/painel")
        }
        assertThat(api.purchaseCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `a response lost after the API received the purchase is uncertain and offers no resend`(browserName: String) {
        val email = api.fundedAccount("100.00")
        withPage(browserName) {
            openPurchase(email)
            reviewPurchase("10,00")
            page.route(PURCHASES_ROUTE) { route: Route ->
                route.fetch()
                route.abort()
            }

            ui.click(confirmButton())

            page.getByText(UNCERTAIN).first().waitFor()
            ui.button("Ver saldo").waitFor()
            ui.button("Ver extrato").waitFor()
            assertThat(ui.button("Tentar de novo").count()).isZero()
            assertThat(confirmButton().count()).isZero()
            assertThat(hasText("Estimativa")).isFalse()
            Axe.assertNoViolations(page)

            ui.click(ui.button("Ver saldo"))
            ui.waitForHash("#/painel")
            page.getByText("R$ 90,00").first().waitFor()
        }
        assertThat(api.purchaseCount(email)).isEqualTo(1)
    }

    private companion object {
        const val ENTER_PRESSES = 5
    }
}

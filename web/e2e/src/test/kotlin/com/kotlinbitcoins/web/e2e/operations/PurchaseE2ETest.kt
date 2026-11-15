package com.kotlinbitcoins.web.e2e.operations

import com.kotlinbitcoins.web.e2e.auth.ApiRequests
import com.kotlinbitcoins.web.e2e.auth.hasText
import com.kotlinbitcoins.web.e2e.auth.waitForAnnouncement
import com.kotlinbitcoins.web.e2e.dashboard.HTTP_SERVICE_UNAVAILABLE
import com.kotlinbitcoins.web.e2e.dashboard.QUOTE_ROUTE
import com.kotlinbitcoins.web.e2e.dashboard.QUOTE_UNAVAILABLE_TEXT
import com.kotlinbitcoins.web.e2e.dashboard.routeProblem
import com.kotlinbitcoins.web.e2e.support.Axe
import com.kotlinbitcoins.web.e2e.support.E2eBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * US4 (T157): review with a labeled estimate that follows the quote, the result of the API without any estimate,
 * and the refusals (invalid amount, insufficient balance, below 1 satoshi, quote unavailable). The quote-stub sells
 * at R$ 400.000,00 and answers with the current instant. axe on editing, review, result and every error.
 */
class PurchaseE2ETest : E2eBase() {
    @ParameterizedTest
    @MethodSource("browsers")
    fun `US4-1 and US4-3 the review shows the estimate and the result shows only the API values`(browserName: String) {
        val email = api.fundedAccount("1000.00")
        withPage(browserName) {
            openPurchase(email)
            Axe.assertNoViolations(page)

            reviewPurchase("400,00")
            page.getByText("Estimativa: 0,00100000 BTC").first().waitFor()
            page.getByText("Cotação de venda usada: R$ 400.000,00").first().waitFor()
            waitForEstimateInstant()
            page.getByText(ESTIMATE_NOTICE).first().waitFor()
            page.getByText("R$ 1.000,00").first().waitFor()
            assertThat(hasText(SATOSHI_WARNING)).isFalse()
            Axe.assertNoViolations(page)

            ui.click(confirmButton())

            waitForPurchaseResult()
            waitForAnnouncement(PURCHASE_ANNOUNCEMENT)
            ui.heading("Valor investido").waitFor()
            ui.heading("BTC comprado").waitFor()
            ui.heading("Cotação utilizada").waitFor()
            ui.heading("Novo saldo").waitFor()
            page.getByText("0,00100000 BTC").first().waitFor()
            page.getByText("R$ 600,00").first().waitFor()
            assertThat(hasText("Estimativa")).describedAs("no estimate on the result").isFalse()
            Axe.assertNoViolations(page)
        }
        assertThat(api.purchaseCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US4-2 a new quote during the review changes the instant of the estimate`(browserName: String) {
        val email = api.fundedAccount("1000.00")
        withPage(browserName) {
            openPurchase(email)
            reviewPurchase("400,00")
            val first = waitForEstimateInstant()
            val focusedBefore = ui.focused()

            page.waitForCondition { estimateInstantText().let { it != null && it != first } }

            page.getByText("Estimativa: 0,00100000 BTC").first().waitFor()
            assertThat(ui.focused()).isEqualTo(focusedBefore)
        }
        assertThat(api.purchaseCount(email)).isZero()
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US4-4 insufficient balance shows the balance and a shortcut to deposit`(browserName: String) {
        val email = api.fundedAccount("100.00")
        withPage(browserName) {
            openPurchase(email)
            reviewPurchase("100,01")

            ui.click(confirmButton())

            page.getByText(INSUFFICIENT_BALANCE).first().waitFor()
            page.getByText("Saldo disponível: R$ 100,00").first().waitFor()
            Axe.assertNoViolations(page)
            // The shortcut "Depositar" of the error (the menu item of the shell comes first).
            ui.click(depositButton())
            waitForDepositForm()
        }
        assertThat(api.purchaseCount(email)).isZero()
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US4-5 invalid amounts do not reach the review and send nothing`(browserName: String) {
        val email = api.fundedAccount("100.00")
        withPage(browserName) {
            openPurchase(email)
            val purchases = ApiRequests(page, "/me/purchases")

            listOf("", "0", "-5", "abc", "1,234").forEach { input ->
                typePurchaseAmount(input)
                ui.click(reviewButton())

                waitForPurchaseAmountError()
                assertThat(ui.heading("Revisar compra").count()).describedAs(input).isZero()
            }

            Axe.assertNoViolations(page)
            assertThat(purchases.count).isZero()
        }
        assertThat(api.purchaseCount(email)).isZero()
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US4-6 below one satoshi warns in the review and the refusal of the API explains it`(browserName: String) {
        val email = api.fundedAccount("1.00")
        withPage(browserName) {
            openPurchase(email)
            // R$ 0,01 is below 1 satoshi only above R$ 1.000.000,00; the quote-stub sells at R$ 400.000,00, so the
            // quote and the refusal of the API (INVALID_AMOUNT, scenario purchaseBelowOneSatoshi) are routed.
            page.routeQuote("2000000.00")
            page.routeProblem(PURCHASES_ROUTE, "INVALID_AMOUNT", HTTP_BAD_REQUEST)
            reviewPurchase("0,01")

            page.getByText("Estimativa: 0,00000000 BTC").first().waitFor()
            page.getByText(SATOSHI_WARNING).first().waitFor()
            assertThat(confirmButton().isEnabled).isTrue()
            Axe.assertNoViolations(page)

            ui.click(confirmButton())

            waitForPurchaseAmountError(INVALID_AMOUNT_SATOSHI)
            Axe.assertNoViolations(page)
        }
        assertThat(api.purchaseCount(email)).isZero()
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US4-7 quote unavailable in the review and on confirmation offers a new attempt`(browserName: String) {
        val email = api.fundedAccount("1000.00")
        withPage(browserName) {
            openPurchase(email)
            page.routeProblem(QUOTE_ROUTE, "QUOTE_UNAVAILABLE", HTTP_SERVICE_UNAVAILABLE)
            page.routeProblem(PURCHASES_ROUTE, "QUOTE_UNAVAILABLE", HTTP_SERVICE_UNAVAILABLE)

            reviewPurchase("400,00")
            page.getByText(QUOTE_UNAVAILABLE_TEXT).first().waitFor()
            assertThat(estimateInstantText()).isNull()
            assertThat(confirmButton().isEnabled).isTrue()
            Axe.assertNoViolations(page)

            ui.click(confirmButton())
            page.getByText(QUOTE_UNAVAILABLE_PURCHASE).first().waitFor()
            Axe.assertNoViolations(page)

            page.unroute(QUOTE_ROUTE)
            page.unroute(PURCHASES_ROUTE)
            ui.click(ui.button("Tentar de novo"))
            waitForReview()
            waitForEstimateInstant()
            assertThat(hasText(QUOTE_UNAVAILABLE_PURCHASE)).isFalse()
        }
        assertThat(api.purchaseCount(email)).isZero()
    }

    private companion object {
        const val HTTP_BAD_REQUEST = 400
    }
}

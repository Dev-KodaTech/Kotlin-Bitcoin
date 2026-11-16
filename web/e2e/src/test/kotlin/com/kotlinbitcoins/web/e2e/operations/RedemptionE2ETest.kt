package com.kotlinbitcoins.web.e2e.operations

import com.kotlinbitcoins.web.e2e.auth.ApiRequests
import com.kotlinbitcoins.web.e2e.auth.hasText
import com.kotlinbitcoins.web.e2e.auth.waitForAnnouncement
import com.kotlinbitcoins.web.e2e.dashboard.HTTP_SERVICE_UNAVAILABLE
import com.kotlinbitcoins.web.e2e.dashboard.POSITION_ROUTE
import com.kotlinbitcoins.web.e2e.dashboard.QUOTE_UNAVAILABLE_TEXT
import com.kotlinbitcoins.web.e2e.dashboard.routeProblem
import com.kotlinbitcoins.web.e2e.support.Axe
import com.kotlinbitcoins.web.e2e.support.E2eBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * US6 (T178): the available with its buy quote, "Resgatar tudo", the review with a labeled estimate, the result of the
 * API with the liquidations (total/partial) and the reinvestment, and the refusals. The data is prepared through the
 * API: a deposit and purchases of different amounts. The quote-stub is shared by every E2E test, so both purchases use
 * its R$ 400.000,00: the FIFO of the API still liquidates the first purchase in total and the second in part. axe on
 * editing, review, result with reinvestment and every error.
 */
class RedemptionE2ETest : E2eBase() {
    @ParameterizedTest
    @MethodSource("browsers")
    fun `US6-1 to US6-3 the available, the estimate and the result with liquidations and reinvestment`(
        browserName: String,
    ) {
        val email = api.investedAccount("1000.00", "400.00", "200.00")
        withPage(browserName) {
            openRedemption(email)
            page.getByText("Disponível para resgate: R$ 600,00").first().waitFor()
            page.getByText("Baseado na cotação de compra de R$ 400.000,00").first().waitFor()
            page.getByText(AVAILABLE_INSTANT.toPattern()).first().waitFor()
            page.getByText(AVAILABLE_NOTICE).first().waitFor()
            Axe.assertNoViolations(page)

            reviewRedemption("500,00")
            page.getByText("Estimativa: 0,00125000 BTC").first().waitFor()
            page.getByText("Cotação de compra usada: R$ 400.000,00").first().waitFor()
            waitForEstimateInstant()
            page.getByText(REDEMPTION_ESTIMATE_NOTICE).first().waitFor()
            assertThat(hasText(REDEEM_ALL_WARNING)).isFalse()
            Axe.assertNoViolations(page)

            ui.click(redemptionConfirmButton())

            waitForRedemptionResult()
            waitForAnnouncement(REDEMPTION_ANNOUNCEMENT)
            listOf("Valor resgatado", "BTC vendido", "Cotação utilizada", "Novo saldo", "Investimentos liquidados")
                .forEach { ui.heading(it).waitFor() }
            page.getByText("R$ 500,00").first().waitFor()
            page.getByText("0,00125000 BTC").first().waitFor()
            page.getByText("R$ 900,00").first().waitFor()
            page.waitForCondition { liquidations().size == 2 }
            assertThat(liquidations())
                .containsExactly(
                    "Liquidação Total: R$ 400,00, 0,00100000 BTC",
                    "Liquidação Parcial: R$ 100,00, 0,00025000 BTC",
                )
            // Below the fold the mirror nodes exist but are not "visible" to waitFor: their presence is checked.
            page.waitForCondition { hasText(REINVESTMENT_EXPLANATION) }
            listOf("Reinvestimento", "BTC residual", "Cotação original", "Data da compra original").forEach {
                assertThat(ui.heading(it).count()).describedAs(it).isEqualTo(1)
            }
            assertThat(hasText("Estimativa")).describedAs("no estimate on the result").isFalse()
            Axe.assertNoViolations(page)
        }
        assertThat(api.redemptionCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US6-4 a redemption without partial liquidation has no reinvestment section`(browserName: String) {
        val email = api.investedAccount("1000.00", "400.00")
        withPage(browserName) {
            openRedemption(email)
            reviewRedemption("400,00")

            ui.click(redemptionConfirmButton())

            waitForRedemptionResult()
            page.waitForCondition { liquidations().size == 1 }
            assertThat(liquidations()).containsExactly("Liquidação Total: R$ 400,00, 0,00100000 BTC")
            assertThat(ui.heading("Reinvestimento").count()).isZero()
            assertThat(hasText(REINVESTMENT_EXPLANATION)).isFalse()
        }
        assertThat(api.redemptionCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US6-5 and US6-6 redeem all fills the field, above the available is refused and redeem all works again`(
        browserName: String,
    ) {
        val email = api.investedAccount("1000.00", "400.00")
        withPage(browserName) {
            openRedemption(email)
            page.getByText("Disponível para resgate: R$ 400,00").first().waitFor()
            val redemptions = ApiRequests(page, "/me/redemptions")

            ui.click(redeemAllButton())
            ui.click(redemptionReviewButton())
            waitForRedemptionReview()
            page.getByText("R$ 400,00").first().waitFor()
            page.getByText("Estimativa: 0,00100000 BTC").first().waitFor()
            page.getByText(REDEEM_ALL_WARNING).first().waitFor()
            assertThat(redemptions.count).describedAs("redeem all never confirms").isZero()
            Axe.assertNoViolations(page)

            ui.click(editAmountButton())
            reviewRedemption("400,01")
            assertThat(hasText(REDEEM_ALL_WARNING)).isFalse()
            ui.click(redemptionConfirmButton())

            page.getByText(REDEMPTION_EXCEEDS).first().waitFor()
            page.getByText("Disponível agora: R$ 400,00").first().waitFor()
            assertThat(redeemAllButton().isEnabled).isTrue()
            Axe.assertNoViolations(page)

            ui.click(redeemAllButton())
            ui.click(redemptionReviewButton())
            waitForRedemptionReview()
            page.getByText(REDEEM_ALL_WARNING).first().waitFor()
        }
        assertThat(api.redemptionCount(email)).isZero()
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US6-7 without investments the available is zero with an invitation and the API refuses`(browserName: String) {
        val email = api.fundedAccount("100.00")
        withPage(browserName) {
            openRedemption(email)
            page.getByText("Disponível para resgate: R$ 0,00").first().waitFor()
            page.getByText(REDEMPTION_EMPTY).first().waitFor()
            ui.button("Comprar bitcoin").waitFor()
            Axe.assertNoViolations(page)

            reviewRedemption("10,00")
            ui.click(redemptionConfirmButton())

            page.getByText(REDEMPTION_EXCEEDS).first().waitFor()
            page.getByText("Disponível agora: R$ 0,00").first().waitFor()
            Axe.assertNoViolations(page)
        }
        assertThat(api.redemptionCount(email)).isZero()
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US6-8 invalid amounts do not reach the review and send nothing`(browserName: String) {
        val email = api.investedAccount("1000.00", "400.00")
        withPage(browserName) {
            openRedemption(email)
            val redemptions = ApiRequests(page, "/me/redemptions")

            listOf("", "0", "-5", "abc", "1,234").forEach { input ->
                typeRedemptionAmount(input)
                ui.click(redemptionReviewButton())

                waitForRedemptionAmountError()
                assertThat(ui.heading("Revisar resgate").count()).describedAs(input).isZero()
            }

            Axe.assertNoViolations(page)
            assertThat(redemptions.count).isZero()
        }
        assertThat(api.redemptionCount(email)).isZero()
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US6-8 quote unavailable in the available and on confirmation offers a new attempt`(browserName: String) {
        val email = api.investedAccount("1000.00", "400.00")
        withPage(browserName) {
            page.routeProblem(POSITION_ROUTE, "QUOTE_UNAVAILABLE", HTTP_SERVICE_UNAVAILABLE)
            page.routeProblem(REDEMPTIONS_ROUTE, "QUOTE_UNAVAILABLE", HTTP_SERVICE_UNAVAILABLE)
            openRedemption(email)
            page.getByText(QUOTE_UNAVAILABLE_TEXT).first().waitFor()
            // Without an available shown, "Resgatar tudo" fills nothing: the review refuses the empty field.
            ui.click(redeemAllButton())
            ui.click(redemptionReviewButton())
            waitForRedemptionAmountError()

            reviewRedemption("100,00")
            assertThat(estimateInstantText()).isNull()
            assertThat(redemptionConfirmButton().isEnabled).isTrue()
            Axe.assertNoViolations(page)

            ui.click(redemptionConfirmButton())
            page.getByText(QUOTE_UNAVAILABLE_REDEMPTION).first().waitFor()
            Axe.assertNoViolations(page)

            page.unroute(POSITION_ROUTE)
            page.unroute(REDEMPTIONS_ROUTE)
            // The retry of the error block (the available block above has its own).
            ui.click(ui.button("Tentar de novo").last())
            waitForRedemptionReview()
            waitForEstimateInstant()
        }
        assertThat(api.redemptionCount(email)).isZero()
    }

    private companion object {
        /** "Atualizada em dd/MM/aaaa hh:mm:ss (horário de Brasília)": the instant of the buy quote of the available. */
        val AVAILABLE_INSTANT =
            Regex("Atualizada em \\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2} \\(horário de Brasília\\)")
    }
}

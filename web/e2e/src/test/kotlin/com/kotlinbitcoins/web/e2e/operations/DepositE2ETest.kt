package com.kotlinbitcoins.web.e2e.operations

import com.kotlinbitcoins.web.e2e.auth.ApiRequests
import com.kotlinbitcoins.web.e2e.auth.headingCount
import com.kotlinbitcoins.web.e2e.auth.waitForAnnouncement
import com.kotlinbitcoins.web.e2e.support.Axe
import com.kotlinbitcoins.web.e2e.support.E2eBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * US3 (T144): a deposit in the Brazilian format shows amount, new balance and instant in Brasília; invalid amounts
 * never reach the API; the dashboard shows the new balance. axe on the editing, error and result states.
 */
class DepositE2ETest : E2eBase() {
    private companion object {
        /** "Data e hora" of the result: "dd/MM/aaaa hh:mm (horário de Brasília)". */
        val DEPOSIT_INSTANT = Regex("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2} \\(horário de Brasília\\)")
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US3-1 a deposit shows the amount, the new balance and the instant, also on the dashboard`(
        browserName: String,
    ) {
        val email = api.registeredAccount()
        withPage(browserName) {
            openDeposit(email)
            Axe.assertNoViolations(page)

            typeAmount("100,00")
            ui.click(depositButton())

            waitForDepositResult("R$ 100,00")
            waitForAnnouncement(DEPOSIT_ANNOUNCEMENT)
            ui.heading("Novo saldo").waitFor()
            page.getByText("R$ 100,00").first().waitFor()
            page.getByText(DEPOSIT_INSTANT.toPattern()).first().waitFor()
            Axe.assertNoViolations(page)

            open("#/painel")
            ui.waitForHash("#/painel")
            page.getByText("R$ 100,00").first().waitFor()
        }
        assertThat(api.depositCount(email)).isEqualTo(1)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US3-2 invalid amounts show the error on the field and send nothing`(browserName: String) {
        val email = api.registeredAccount()
        withPage(browserName) {
            openDeposit(email)
            val deposits = ApiRequests(page, "/me/deposits")

            listOf("", "0", "-5", "abc", "1,234").forEach { input ->
                typeAmount(input)
                ui.click(depositButton())

                waitForAmountError()
                val focused = ui.focused()
                assertThat(focused?.label).describedAs(input).isEqualTo(DEPOSIT_FIELD)
                assertThat(focused?.isInvalid).describedAs(input).isTrue()
                assertThat(focused?.description).describedAs(input).isEqualTo(INVALID_AMOUNT)
            }

            Axe.assertNoViolations(page)
            assertThat(deposits.count).isZero()
        }
        assertThat(api.depositCount(email)).isZero()
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US3-3 the Brazilian format with thousands is accepted`(browserName: String) {
        val email = api.registeredAccount()
        withPage(browserName) {
            openDeposit(email)

            typeAmount("1.234,56")
            ui.press("Enter")

            waitForDepositResult("R$ 1.234,56")
            ui.click(ui.button("Novo depósito"))
            waitForDepositForm()
            ui.textbox(DEPOSIT_FIELD).waitFor()
            assertThat(headingCount("Depósito de R$ 1.234,56 realizado")).isZero()
        }
        assertThat(api.depositCount(email)).isEqualTo(1)
    }
}

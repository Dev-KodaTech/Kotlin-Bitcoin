package com.kotlinbitcoins.web.e2e.dashboard

import com.kotlinbitcoins.web.e2e.auth.PASSWORD
import com.kotlinbitcoins.web.e2e.auth.hasText
import com.kotlinbitcoins.web.e2e.support.Axe
import com.kotlinbitcoins.web.e2e.support.E2eBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * US2 (T131): greeting with the first name, balance, quote with its instant in Brasília (the context runs in
 * New York, E2eBase), current total, the three shortcuts and the greeting without name when `GET /me` fails.
 */
class DashboardE2ETest : E2eBase() {
    private fun registered(
        name: String = "Maria Souza",
        deposit: String? = null,
    ): String {
        val email = api.uniqueEmail()
        api.register(name, email, PASSWORD)
        if (deposit != null) {
            val response = api.deposit(api.login(email, PASSWORD), deposit)
            check(response.status == HTTP_CREATED) { "deposit failed: ${response.status}" }
        }
        return email
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US2-1 greeting, balance, quote with its instant in Brasilia and the total`(browserName: String) {
        val email = registered(deposit = "1234.56")
        withPage(browserName) {
            openDashboard(email)

            ui.heading("Olá, Maria").waitFor()
            page.getByText("R$ 1.234,56").first().waitFor()
            page.getByText(Regex("^Cotação de compra: ").toPattern()).first().waitFor()
            page.getByText(Regex("^Cotação de venda: ").toPattern()).first().waitFor()
            waitForQuoteInstant()
            ui.heading("Valor atual dos investimentos").waitFor()
            Axe.assertNoViolations(page)
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US2-4 without investments the total is zero with an invitation`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            openDashboard(email)

            page.getByText(NO_INVESTMENTS).waitFor()
            assertThat(page.getByText("R$ 0,00").count()).isGreaterThanOrEqualTo(2)
            Axe.assertNoViolations(page)
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US2-5 the three shortcuts open their operations`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            openDashboard(email)

            listOf("Depositar" to "#/depositar", "Comprar" to "#/comprar", "Resgatar" to "#/resgatar")
                .forEach { (shortcut, fragment) ->
                    ui.click(shortcut(shortcut))
                    ui.waitForHash(fragment)
                    page.goBack()
                    ui.waitForHash("#/painel")
                    ui.heading("Olá, Maria").waitFor()
                }
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US2-7 a failed profile greets without the name and the rest works`(browserName: String) {
        val email = registered(deposit = "10.00")
        withPage(browserName) {
            page.routeProblem("**/api/v1/me", "INTERNAL_ERROR", HTTP_INTERNAL_ERROR)
            openDashboard(email)

            ui.heading("Olá!").waitFor()
            page.getByText("R$ 10,00").first().waitFor()
            assertThat(hasText("Olá, Maria")).isFalse()
            waitForQuoteInstant()
            Axe.assertNoViolations(page)
        }
    }

    private companion object {
        const val HTTP_CREATED = 201
    }
}

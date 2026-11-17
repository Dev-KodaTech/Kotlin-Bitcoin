package com.kotlinbitcoins.web.e2e.dashboard

import com.kotlinbitcoins.web.e2e.auth.PASSWORD
import com.kotlinbitcoins.web.e2e.auth.hasText
import com.kotlinbitcoins.web.e2e.support.Axe
import com.kotlinbitcoins.web.e2e.support.E2eBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * US2-3 and the Edge Case "cotação volta" (T133): QUOTE_UNAVAILABLE shows only the message (no old values), the
 * total depends on the quote, balance, shortcuts and "Sair" keep working, the transition is announced once, and the
 * values come back by themselves. The slow variant stops the real quote-stub (SC-006).
 */
class QuoteUnavailableE2ETest : E2eBase() {
    private fun registered(): String =
        api.uniqueEmail().also {
            api.register("Maria Souza", it, PASSWORD)
            api.deposit(api.login(it, PASSWORD), "50.00")
        }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US2-3 quote unavailable keeps the rest of the dashboard and comes back by itself`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            openDashboard(email)
            waitForQuoteInstant()

            page.routeProblem(QUOTE_ROUTE, "QUOTE_UNAVAILABLE", HTTP_SERVICE_UNAVAILABLE)
            page.routeProblem(POSITION_ROUTE, "QUOTE_UNAVAILABLE", HTTP_SERVICE_UNAVAILABLE)
            page.getByText(QUOTE_UNAVAILABLE_TEXT).first().waitFor()
            page.getByText(DEPENDS_ON_QUOTE).waitFor()
            page.waitForFunction(
                "t => document.getElementById('live-polite').textContent === t",
                QUOTE_UNAVAILABLE_TEXT,
            )

            assertThat(quoteInstantText()).isNull()
            assertThat(hasText("R$ 50,00")).isTrue()
            ui.heading("Olá, Maria").waitFor()
            Axe.assertNoViolations(page)

            // Further cycles do not announce again.
            page.evaluate("() => document.getElementById('live-polite').textContent = ''")
            page.waitForTimeout(CYCLE_MS)
            assertThat(ui.liveRegionText("live-polite")).isEmpty()

            page.unroute(QUOTE_ROUTE)
            page.unroute(POSITION_ROUTE)
            waitForQuoteInstant()
            page.getByText(DEPENDS_ON_QUOTE).waitFor(
                com.microsoft.playwright.Locator
                    .WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.DETACHED),
            )

            ui.click(shortcut("Depositar"))
            ui.waitForHash("#/depositar")
        }
    }

    @Tag("slow")
    @ParameterizedTest
    @MethodSource("browsers")
    fun `SC-006 the real quote source stopped shows the unavailable block`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            openDashboard(email)
            compose("stop")
            try {
                // The API may still serve a cached quote for up to 60 s (FR-012a of the 001).
                page.getByText(QUOTE_UNAVAILABLE_TEXT).first().waitFor(
                    com.microsoft.playwright.Locator
                        .WaitForOptions()
                        .setTimeout(SOURCE_DOWN_TIMEOUT_MS),
                )
                assertThat(hasText("R$ 50,00")).isTrue()
            } finally {
                compose("start")
            }
        }
    }

    private fun compose(command: String) {
        val process = ProcessBuilder("docker", "compose", command, "quote-stub").inheritIO().start()
        check(process.waitFor() == 0) { "docker compose $command quote-stub failed" }
    }

    private companion object {
        const val CYCLE_MS = 12_000.0
        const val SOURCE_DOWN_TIMEOUT_MS = 120_000.0
    }
}

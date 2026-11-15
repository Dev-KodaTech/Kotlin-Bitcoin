package com.kotlinbitcoins.web.e2e.dashboard

import com.kotlinbitcoins.web.e2e.auth.ApiRequests
import com.kotlinbitcoins.web.e2e.auth.PASSWORD
import com.kotlinbitcoins.web.e2e.support.E2eBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * US2-2, US2-6 and SC-005 (T132): the quote refreshes by itself every ~10 s without moving the focus, the pause
 * stops it (visible and announced), a hidden page stops it and refreshes at once when visible again. The quote-stub
 * answers with the current instant, so a new quote has a new "Atualizada em".
 */
class AutoRefreshE2ETest : E2eBase() {
    private fun registered(): String = api.uniqueEmail().also { api.register("Maria Souza", it, PASSWORD) }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US2-2 the quote changes by itself and the focus stays on the shortcut`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            openDashboard(email)
            val first = waitForQuoteInstant()
            shortcut("Comprar").focus()
            val focusedBefore = ui.focused()

            page.waitForTimeout(REFRESH_WAIT_MS)

            assertThat(quoteInstantText()).isNotEqualTo(first)
            assertThat(ui.focused()).isEqualTo(focusedBefore)
            assertThat(ui.hash()).isEqualTo("#/painel")
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US2-6 the pause is visible, announced and stops the requests`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            openDashboard(email)
            waitForQuoteInstant()
            ui.click(ui.button("Atualização automática: ativa"))
            ui.button("Atualização automática: pausada").waitFor()
            page.waitForFunction(
                "t => document.getElementById('live-polite').textContent === t",
                PAUSED_ANNOUNCEMENT,
            )

            val quotes = ApiRequests(page, "/quotes/current")
            page.waitForTimeout(PAUSE_WATCH_MS)

            assertThat(quotes.count).isZero()
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `a hidden page stops the refresh and refreshes at once when visible again`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            openDashboard(email)
            waitForQuoteInstant()
            val quotes = ApiRequests(page, "/quotes/current")

            setVisibility("hidden")
            page.waitForTimeout(REFRESH_WAIT_MS)
            assertThat(quotes.count).isZero()

            setVisibility("visible")
            page.waitForTimeout(IMMEDIATE_WAIT_MS)
            assertThat(quotes.count).isGreaterThanOrEqualTo(1)
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `SC-005 the quote shown is never older than 25 s`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            openDashboard(email)
            waitForQuoteInstant()
            val deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos()
            while (System.nanoTime() < deadline) {
                val shown = waitForQuoteInstant()
                val quotedAt = parseInstant(shown)
                val age = Duration.between(quotedAt, OffsetDateTime.now(BRASILIA))
                assertThat(age).describedAs(shown).isLessThanOrEqualTo(Duration.ofSeconds(MAX_AGE_SECONDS))
                page.waitForTimeout(SAMPLE_MS)
            }
        }
    }

    private fun com.kotlinbitcoins.web.e2e.support.AppPage.setVisibility(state: String) {
        page.evaluate(
            """s => {
                Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => s });
                Object.defineProperty(document, 'hidden', { configurable: true, get: () => s === 'hidden' });
                document.dispatchEvent(new Event('visibilitychange'));
            }""",
            state,
        )
    }

    private fun parseInstant(text: String): OffsetDateTime {
        val raw = checkNotNull(Regex("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}").find(text)).value
        return java.time.LocalDateTime
            .parse(raw, FORMAT)
            .atZone(BRASILIA)
            .toOffsetDateTime()
    }

    private companion object {
        val BRASILIA: ZoneId = ZoneId.of("America/Sao_Paulo")
        val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        const val REFRESH_WAIT_MS = 15_000.0
        const val PAUSE_WATCH_MS = 25_000.0
        const val IMMEDIATE_WAIT_MS = 2_000.0
        const val SAMPLE_MS = 5_000.0
        const val MAX_AGE_SECONDS = 25L
    }
}

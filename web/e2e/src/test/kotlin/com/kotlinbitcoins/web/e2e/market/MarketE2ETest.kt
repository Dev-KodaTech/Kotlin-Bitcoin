package com.kotlinbitcoins.web.e2e.market

import com.kotlinbitcoins.web.e2e.auth.PASSWORD
import com.kotlinbitcoins.web.e2e.auth.login
import com.kotlinbitcoins.web.e2e.auth.waitForLogin
import com.kotlinbitcoins.web.e2e.dashboard.fixturesDir
import com.kotlinbitcoins.web.e2e.support.AppPage
import com.kotlinbitcoins.web.e2e.support.Axe
import com.kotlinbitcoins.web.e2e.support.E2eBase
import com.kotlinbitcoins.web.e2e.support.Snapshots
import com.kotlinbitcoins.web.e2e.support.Viewport
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import com.microsoft.playwright.options.Clip
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * US8 (T207). The volume of the day against the real API (any value: it is the market of every client) and, with
 * `page.route` serving the contract fixtures, the exact totals (US8-1), the chart with its legend, summary and gap
 * (US8-2, US8-3; screenshot compared in Chromium only), the table by keyboard (US8-4), the empty history without
 * touching the volume (US8-5) and "Atualizar" as the only refresh (FR-035). axe in each state.
 */
class MarketE2ETest : E2eBase() {
    private fun registered(): String = api.uniqueEmail().also { api.register("Ana Lima", it, PASSWORD) }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US8-1 the volume of today from the API with 8 decimals and the date of reference`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            openMarket(email)

            ui.heading("Volume de hoje (${today()})").waitFor()
            assertThat(page.getByText(BTC_TOTAL).count()).isGreaterThanOrEqualTo(2)
            Axe.assertNoViolations(page)
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US8-1 the totals of the response exactly`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            page.routeFixture(VOLUME_ROUTE, "success/market-daily-volume-200.json")
            openMarket(email)

            ui.heading("Volume de hoje (09/11/2026)").waitFor()
            page.getByText("0,00300000 BTC").waitFor()
            page.getByText("0,00050000 BTC").waitFor()
            page.getByText("BTC comprados").first().waitFor()
            assertThat(page.getByText("BTC comprados").count()).isEqualTo(1)
            assertThat(page.getByText("BTC vendidos").count()).isEqualTo(1)
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US8-2 and US8-3 two lines with legend and summary, and the gap as a row without record`(browserName: String) {
        val email = registered()
        withPage(browserName, viewport = TALL) {
            page.routeFixture(VOLUME_ROUTE, "success/market-daily-volume-200.json")
            page.routeFixture(HISTORY_ROUTE, "success/quotes-history-200-with-gap.json")
            openMarket(email)

            ui.heading("Últimas 24 horas (horário de Brasília)").waitFor()
            assertThat(label(chart())).isEqualTo(SUMMARY)
            assertThat(labelled("Preço de compra, linha contínua com círculos").count()).isEqualTo(1)
            assertThat(labelled("Preço de venda, linha tracejada com quadrados").count()).isEqualTo(1)
            Axe.assertNoViolations(page)

            if (browserName == "chromium") {
                page.waitForTimeout(SETTLE_MS)
                Snapshots.assertMatches("market/price-history-gap", page.screenshot(clipOf(chart())))
            }

            ui.click(ui.button("Ver dados em tabela"))
            ui.button("Ocultar tabela").waitFor()
            labelled("08/11/2026 08:20: compra R$ 398.000,00, venda R$ 399.000,00").waitFor()
            scrollUntilVisible(labelled("09/11/2026 02:00: sem registro"))
            assertThat(labelled("09/11/2026 01:50: compra ", prefix = true).count()).isEqualTo(1)
            assertThat(labelled("09/11/2026 02:10: compra ", prefix = true).count()).isEqualTo(1)
            Axe.assertNoViolations(page)
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US8-4 the summary is the name of the chart and the table opens and closes by keyboard`(browserName: String) {
        val email = registered()
        withPage(browserName, viewport = TALL) {
            page.routeFixture(HISTORY_ROUTE, "success/quotes-history-200-with-gap.json")
            openMarket(email)
            assertThat(label(chart())).contains("Preço de compra: mínimo", "Preço de venda: mínimo")

            // The h1 has the focus on arrival (FR-046): Tab reaches "Atualizar", then "Ver dados em tabela".
            ui.press("Tab", times = TABS_TO_TABLE_BUTTON)
            ui.press("Enter")
            ui.button("Ocultar tabela").waitFor()
            labelled("08/11/2026 08:20: compra ", prefix = true).first().waitFor()

            // The focus stays on the disclosure: Enter again closes the table.
            ui.press("Enter")
            ui.button("Ver dados em tabela").waitFor()
            assertThat(labelled("08/11/2026 08:20: compra ", prefix = true).count()).isZero()
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US8-5 an empty history shows the empty state and keeps the volume`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            page.routeFixture(VOLUME_ROUTE, "success/market-daily-volume-200.json")
            page.routeFixture(HISTORY_ROUTE, "success/quotes-history-200-empty.json")
            openMarket(email)

            page.getByText("Sem dados de cotação nas últimas 24 horas.").waitFor()
            page.getByText("0,00300000 BTC").waitFor()
            assertThat(ui.button("Ver dados em tabela").count()).isZero()
            Axe.assertNoViolations(page)
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `FR-035 the page loads on opening and again only by Atualizar`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            val calls = mutableListOf<String>()
            page.onRequest { request ->
                if (request.url().contains("/market/daily-volume") || request.url().contains("/quotes/history")) {
                    calls += request.url()
                }
            }
            openMarket(email)
            ui.heading("Volume de hoje (${today()})").waitFor()
            page.waitForTimeout(NO_AUTO_REFRESH_MS)
            assertThat(calls).describedAs("requests before Atualizar").hasSize(2)

            ui.click(ui.button("Atualizar"))
            page.waitForTimeout(SETTLE_MS)
            assertThat(calls).describedAs("requests after Atualizar").hasSize(4)
        }
    }

    /** Opens the Mercado without a session: the gate sends to the login, which returns to the page. */
    private fun AppPage.openMarket(email: String) {
        open(MARKET)
        waitForLogin()
        login(email)
        ui.waitForHash(MARKET)
        ui.heading("Mercado").first().waitFor()
        page.waitForFunction("t => document.title === t", TITLE)
    }

    private fun AppPage.chart(): Locator = page.locator("[aria-label^='Gráfico de preços']").first()

    private fun AppPage.labelled(
        text: String,
        prefix: Boolean = false,
    ): Locator {
        // The mirror may write the space after "R$" as a no-break space: match both.
        val variants = listOf(text, text.replace("R$ ", "R$ ")).distinct()
        val operator = if (prefix) "^=" else "="
        return page.locator(variants.joinToString(", ") { "[aria-label$operator\"$it\"]" })
    }

    private fun label(locator: Locator): String {
        locator.waitFor()
        return locator.getAttribute("aria-label").orEmpty().replace(' ', ' ')
    }

    private fun AppPage.scrollUntilVisible(target: Locator) {
        page.mouse().move(SCROLL_X, SCROLL_Y)
        repeat(MAX_SCROLLS) {
            if (target.count() > 0) return
            page.mouse().wheel(0.0, SCROLL_STEP)
            page.waitForTimeout(SCROLL_SETTLE_MS)
        }
        assertThat(target.count()).describedAs("row reached by scrolling").isPositive()
    }

    private fun clipOf(locator: Locator): Page.ScreenshotOptions {
        val box = requireNotNull(locator.boundingBox()) { "chart without bounding box" }
        return Page.ScreenshotOptions().setClip(Clip(box.x, box.y, box.width, box.height))
    }

    private fun Page.routeFixture(
        glob: String,
        file: String,
    ) {
        val body = Files.readString(fixturesDir().resolve(file))
        route(glob) { route: Route ->
            route.fulfill(
                Route
                    .FulfillOptions()
                    .setStatus(HTTP_OK)
                    .setContentType("application/json")
                    .setBody(body),
            )
        }
    }

    private fun today(): String =
        LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    private companion object {
        const val MARKET = "#/mercado"
        const val TITLE = "Mercado · Investimento em Bitcoins"
        const val VOLUME_ROUTE = "**/api/v1/market/daily-volume"
        const val HISTORY_ROUTE = "**/api/v1/quotes/history"
        const val HTTP_OK = 200
        const val SETTLE_MS = 1_000.0
        const val NO_AUTO_REFRESH_MS = 12_000.0
        const val TABS_TO_TABLE_BUTTON = 2
        const val MAX_SCROLLS = 60
        const val SCROLL_STEP = 400.0
        const val SCROLL_SETTLE_MS = 150.0
        const val SCROLL_X = 700.0
        const val SCROLL_Y = 500.0
        val TALL = Viewport("tall", 1280, 1400)
        val BTC_TOTAL = Regex("""^\d+,\d{8} BTC$""").toPattern()
        const val SUMMARY =
            "Gráfico de preços das últimas 24 horas (horário de Brasília). " +
                "Preço de compra: mínimo R$ 398.000,00, máximo R$ 401.705,12, último R$ 398.255,59 às 08:10. " +
                "Preço de venda: mínimo R$ 399.000,00, máximo R$ 402.705,12, último R$ 399.255,59 às 08:10."
    }
}

package com.kotlinbitcoins.web.e2e.statement

import com.kotlinbitcoins.web.e2e.auth.PASSWORD
import com.kotlinbitcoins.web.e2e.auth.SESSION_EXPIRED
import com.kotlinbitcoins.web.e2e.auth.fillField
import com.kotlinbitcoins.web.e2e.auth.login
import com.kotlinbitcoins.web.e2e.auth.waitForDashboard
import com.kotlinbitcoins.web.e2e.auth.waitForLogin
import com.kotlinbitcoins.web.e2e.support.AppPage
import com.kotlinbitcoins.web.e2e.support.Axe
import com.kotlinbitcoins.web.e2e.support.E2eBase
import com.kotlinbitcoins.web.e2e.support.NoHorizontalScroll
import com.kotlinbitcoins.web.e2e.support.Viewport
import com.kotlinbitcoins.web.e2e.support.Viewports
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * US7 (T193) against the real API (quote-stub at R$ 400.000,00): a deposit of R$ 1.000,00, purchases of R$ 400,00 and
 * R$ 200,00 and a redemption of R$ 500,00 (two REDEMPTION lines and one REINVESTMENT with the same requestId). Default
 * period, order and grouping (US7-1 to US7-4); the filter in the URL surviving reload, back/forward and the login after
 * the session expires (US7-5, FR-027, SC-009); the invalid range at the fields keeping the statement (US7-6); the empty
 * state with the period (US7-7); 300 entries by keyboard; no connection when opening the page; axe in each state.
 */
class StatementE2ETest : E2eBase() {
    /** An account with the operations of the Independent Test of US7; returns its e-mail. */
    private fun accountWithRedemption(): String {
        val email = api.uniqueEmail()
        api.register("Maria Souza", email, PASSWORD)
        val token = api.login(email, PASSWORD)
        check(api.deposit(token, "1000.00").status == HTTP_CREATED)
        check(api.purchase(token, "400.00").status == HTTP_CREATED)
        check(api.purchase(token, "200.00").status == HTTP_CREATED)
        check(api.redeem(token, "500.00").status == HTTP_CREATED)
        return email
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US7-1 to US7-4 default period, newest first, the redemption grouped and the reinvestment marked`(
        browserName: String,
    ) {
        val email = accountWithRedemption()
        withPage(browserName, viewport = TALL) {
            openStatement(email)
            waitForCards(EXPECTED_CARDS)

            page.getByText(PERIOD.toPattern()).first().waitFor()
            val labels = cardLabels()
            assertThat(labels[0]).startsWith("Resgate — total R$ 500,00, em ")
            assertThat(labels[0])
                .contains("Resgate de R$ 400,00 em ", ", 0,00100000 BTC, cotação R$ 400.000,00")
                .contains("Resgate de R$ 100,00 em ", ", 0,00025000 BTC")
                .contains("Reinvestimento de R$ 100,00 em ")
                .contains(", não altera o saldo")
            assertThat(labels[1]).startsWith("Compra de R$ 200,00 em ").endsWith(", 0,00050000 BTC, $QUOTE")
            assertThat(labels[2]).startsWith("Compra de R$ 400,00 em ").endsWith(", 0,00100000 BTC, $QUOTE")
            assertThat(labels[3])
                .startsWith("Depósito de R$ 1.000,00 em ")
                .doesNotContain("BTC")
                .doesNotContain("cotação")
            Axe.assertNoViolations(page)
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US7-5 the filter is in the URL and survives back, forward and reload, and clearing returns`(
        browserName: String,
    ) {
        val email = accountWithRedemption()
        withPage(browserName, viewport = TALL) {
            openStatement(email)
            waitForCards(EXPECTED_CARDS)

            applyFilter(today(), today())
            val filtered = "#/extrato?de=${todayIso()}&ate=${todayIso()}"
            ui.waitForHash(filtered)
            waitForCards(EXPECTED_CARDS)

            ui.click(navItem("Painel"))
            waitForDashboard()
            page.goBack()
            ui.waitForHash(filtered)
            waitForCards(EXPECTED_CARDS)
            page.goForward()
            waitForDashboard()
            page.goBack()
            ui.waitForHash(filtered)

            page.reload()
            waitForApp()
            ui.waitForHash(filtered)
            waitForCards(EXPECTED_CARDS)
            assertThat(fieldValue("Data inicial")).isEqualTo(today())
            assertThat(fieldValue("Data final")).isEqualTo(today())

            ui.click(ui.button("Limpar filtro"))
            ui.waitForHash(STATEMENT)
            waitForCards(EXPECTED_CARDS)
            assertThat(fieldValue("Data inicial")).isEmpty()
        }
    }

    /** contracts/ui-routes.md: applying the filter **replaces** the history entry (HashRouter, research W-04). */
    @ParameterizedTest
    @MethodSource("browsers")
    fun `US7-5 applying the filter replaces the history entry`(browserName: String) {
        val email = accountWithRedemption()
        withPage(browserName, viewport = TALL) {
            openStatement(email)
            waitForCards(EXPECTED_CARDS)
            ui.click(navItem("Painel"))
            waitForDashboard()
            ui.click(navItem("Extrato"))
            ui.waitForHash(STATEMENT)
            waitForCards(EXPECTED_CARDS)

            applyFilter(today(), today())
            ui.waitForHash("#/extrato?de=${todayIso()}&ate=${todayIso()}")

            page.goBack()
            page.waitForTimeout(SETTLE_MS)
            assertThat(ui.hash()).describedAs("back after applying the filter").isEqualTo("#/painel")
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US7-6 an invalid range shows the message at the dates and keeps the statement`(browserName: String) {
        val email = accountWithRedemption()
        withPage(browserName, viewport = TALL) {
            openStatement(email)
            waitForCards(EXPECTED_CARDS)

            for ((from, to) in listOf("10/11/2026" to "01/11/2026", "01/10/2026" to "", "31/02/2026" to "09/11/2026")) {
                applyFilter(from, to)

                page.waitForCondition { ui.focused()?.description == INVALID_RANGE }
                assertThat(ui.hash()).isEqualTo(STATEMENT)
                assertThat(cardLabels()).hasSize(EXPECTED_CARDS)
                val focused = ui.focused()
                assertThat(focused?.label).isEqualTo("Data inicial")
                assertThat(focused?.isInvalid).isTrue()
                assertThat(focused?.description).isEqualTo(INVALID_RANGE)
            }
            Axe.assertNoViolations(page)
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `an invalid range in the URL shows the default period with the message and the fields as they came`(
        browserName: String,
    ) {
        val email = accountWithRedemption()
        withPage(browserName, viewport = TALL) {
            openStatement(email, "#/extrato?de=2026-11-10&ate=2026-11-01")

            waitForCards(EXPECTED_CARDS)
            ui.tabTo("Data inicial")
            assertThat(ui.focused()?.isInvalid).isTrue()
            assertThat(ui.focused()?.description).isEqualTo(INVALID_RANGE)
            assertThat(fieldValue("Data inicial")).isEqualTo("10/11/2026")
            assertThat(fieldValue("Data final")).isEqualTo("01/11/2026")
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US7-7 a period without entries shows the empty state with the period`(browserName: String) {
        val email = accountWithRedemption()
        withPage(browserName, viewport = TALL) {
            openStatement(email)
            waitForCards(EXPECTED_CARDS)

            applyFilter("01/01/2020", "02/01/2020")

            ui.waitForHash("#/extrato?de=2020-01-01&ate=2020-01-02")
            page.getByText("Nenhum lançamento no período de 01/01/2020 00:00 a ").first().waitFor()
            assertThat(cards().count()).isZero()
            Axe.assertNoViolations(page)
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `expiry with a filter returns to the statement with the same filter after login`(browserName: String) {
        val email = accountWithRedemption()
        val filtered = "#/extrato?de=${todayIso()}&ate=${todayIso()}"
        withPage(browserName, viewport = TALL, prepare = { it.clock().install() }) {
            openStatement(email, filtered)
            waitForCards(EXPECTED_CARDS)

            page.clock().fastForward(PAST_EXPIRY)
            waitForLogin()
            page.getByText(SESSION_EXPIRED).first().waitFor()

            login(email)
            ui.waitForHash(filtered)
            waitForCards(EXPECTED_CARDS)
            assertThat(fieldValue("Data inicial")).isEqualTo(today())
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `300 entries are reachable by keyboard without horizontal scroll`(browserName: String) {
        val email = api.uniqueEmail()
        api.register("Maria Souza", email, PASSWORD)
        val token = api.login(email, PASSWORD)
        (1..MANY_ENTRIES).forEach { check(api.deposit(token, "$it.00").status == HTTP_CREATED) }
        withPage(browserName, viewport = Viewports.PHONE_SMALL) {
            openStatement(email)
            cards().first().waitFor()
            assertThat(cardLabels().first()).startsWith("Depósito de R$ 300,00 em ")
            NoHorizontalScroll.assertOn(page)

            // The LazyColumn composes only the visible cards; Tab moves card by card and the list follows the focus.
            val oldest = "Depósito de R$ 1,00 em "
            repeat(MANY_ENTRIES + MAX_EXTRA_TABS) { if (cardLabels().none { it.startsWith(oldest) }) ui.press("Tab") }
            page.waitForCondition { cardLabels().any { it.startsWith(oldest) } }
            NoHorizontalScroll.assertOn(page)
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `no connection when opening the page shows the message and retry, with the navigation intact`(
        browserName: String,
    ) {
        val email = accountWithRedemption()
        withPage(browserName, viewport = TALL) {
            open("#/painel")
            waitForLogin()
            login(email)
            waitForDashboard()

            page.context().setOffline(true)
            ui.click(navItem("Extrato"))
            ui.waitForHash(STATEMENT)
            page.getByText(NO_CONNECTION).first().waitFor()
            ui.button("Tentar de novo").waitFor()
            Axe.assertNoViolations(page)

            page.context().setOffline(false)
            ui.click(ui.button("Tentar de novo"))
            waitForCards(EXPECTED_CARDS)
            ui.click(navItem("Painel"))
            waitForDashboard()
        }
    }

    /** Opens [fragment] without a session: the gate sends to the login, which returns to the page. */
    private fun AppPage.openStatement(
        email: String,
        fragment: String = STATEMENT,
    ) {
        open(fragment)
        waitForLogin()
        login(email)
        ui.waitForHash(fragment)
        ui.heading("Extrato").first().waitFor()
        page.waitForFunction("t => document.title === t", TITLE)
    }

    private fun AppPage.applyFilter(
        from: String,
        to: String,
    ) {
        fillField("Data inicial", from)
        fillField("Data final", to)
        ui.click(ui.button("Aplicar filtro"))
    }

    /** The text of a date field, as its mirror node (a contenteditable `textbox`) shows it. */
    private fun AppPage.fieldValue(label: String): String =
        ui
            .textbox(label)
            .textContent()
            .orEmpty()
            .trim()

    /** Each item is one mirror node named by its full sentence (W-14). */
    private fun AppPage.cards(): Locator =
        page.locator(
            "[aria-label^='Resgate — total '], [aria-label^='Depósito de '], [aria-label^='Compra de '], " +
                "[aria-label^='Resgate de '], [aria-label^='Reinvestimento de ']",
        )

    /** The mirror may write the space after "R$" as a no-break space; spaces are normalized before comparing. */
    @Suppress("UNCHECKED_CAST")
    private fun AppPage.cardLabels(): List<String> =
        // One atomic read: the LazyColumn recycles the nodes while it scrolls.
        (cards().evaluateAll("els => els.map(e => e.getAttribute('aria-label') || '')") as List<String>)
            .map { it.replace('\u00A0', ' ') }

    private fun AppPage.waitForCards(count: Int) {
        cards().first().waitFor()
        repeat(CARD_POLLS) { if (cards().count() != count) page.waitForTimeout(CARD_POLL_MS) }
        assertThat(cardLabels()).describedAs("statement cards").hasSize(count)
    }

    private fun AppPage.navItem(name: String): Locator =
        page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(name).setExact(true)).first()

    private fun today(): String = LocalDate.now(BRASILIA).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    private fun todayIso(): String = LocalDate.now(BRASILIA).toString()

    private companion object {
        const val STATEMENT = "#/extrato"
        const val TITLE = "Extrato · Investimento em Bitcoins"
        const val INVALID_RANGE = "Intervalo de datas inválido. A data inicial deve ser igual ou anterior à final."
        const val NO_CONNECTION = "Não foi possível conectar. Verifique sua internet e tente novamente."
        const val EXPECTED_CARDS = 4
        const val MANY_ENTRIES = 300
        const val MAX_EXTRA_TABS = 20
        const val HTTP_CREATED = 201
        const val CARD_POLLS = 40
        const val SETTLE_MS = 1_000.0
        const val CARD_POLL_MS = 250.0

        /** `expiresIn` of the API (3600 s) plus a margin. */
        const val PAST_EXPIRY = "01:00:05"
        const val QUOTE = "cotação R$ 400.000,00"
        val PERIOD = Regex("^Período: \\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2} a .*\\(horário de Brasília\\)$")

        /** Desktop width, tall enough for the filter and the four items of the statement composed at once. */
        val TALL = Viewport("1280-tall", 1280, 1800)
        val BRASILIA: ZoneId = ZoneId.of("America/Sao_Paulo")
    }
}

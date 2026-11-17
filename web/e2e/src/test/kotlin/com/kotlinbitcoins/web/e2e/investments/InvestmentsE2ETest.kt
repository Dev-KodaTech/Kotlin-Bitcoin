package com.kotlinbitcoins.web.e2e.investments

import com.kotlinbitcoins.web.e2e.auth.ApiRequests
import com.kotlinbitcoins.web.e2e.auth.PASSWORD
import com.kotlinbitcoins.web.e2e.auth.hasText
import com.kotlinbitcoins.web.e2e.auth.login
import com.kotlinbitcoins.web.e2e.auth.waitForLogin
import com.kotlinbitcoins.web.e2e.dashboard.HTTP_SERVICE_UNAVAILABLE
import com.kotlinbitcoins.web.e2e.dashboard.PAUSED_ANNOUNCEMENT
import com.kotlinbitcoins.web.e2e.dashboard.POSITION_ROUTE
import com.kotlinbitcoins.web.e2e.dashboard.QUOTE_UNAVAILABLE_TEXT
import com.kotlinbitcoins.web.e2e.dashboard.routeFixture
import com.kotlinbitcoins.web.e2e.dashboard.routeProblem
import com.kotlinbitcoins.web.e2e.support.AppPage
import com.kotlinbitcoins.web.e2e.support.Axe
import com.kotlinbitcoins.web.e2e.support.E2eBase
import com.kotlinbitcoins.web.e2e.support.NoHorizontalScroll
import com.kotlinbitcoins.web.e2e.support.Viewports
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * US5 (T168): each investment of `me-investments-200.json` (served by `page.route`) is a card read at once, in the API
 * order, with the variation told by symbol and text besides the color; total and reference quote with its instant;
 * the empty state with the shortcut to buy (real API); the unavailable quote with "Tentar de novo"; the refresh every
 * ~10 s without moving the focus; cards at 320 px; axe in the loaded, empty and unavailable states.
 */
class InvestmentsE2ETest : E2eBase() {
    private fun registered(): String = api.uniqueEmail().also { api.register("Maria Souza", it, PASSWORD) }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US5-1 to US5-3 every field, the variation beyond color, the total and the reference quote`(
        browserName: String,
    ) {
        val email = registered()
        withPage(browserName) {
            page.routeFixture(POSITION_ROUTE, "me-investments-200.json")
            openInvestments(email)
            waitForCards(FIXTURE_CARDS.size)

            assertThat(cardTexts()).containsExactlyElementsOf(FIXTURE_CARDS)
            page.getByText("R$ 704,00").first().waitFor()
            page.getByText("Cotação de referência: R$ 440.000,00").first().waitFor()
            page.getByText("Atualizada em 09/11/2026 08:10:00 (horário de Brasília)").first().waitFor()
            Axe.assertNoViolations(page)
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US5-4 without investments the empty state invites to buy`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            openInvestments(email)

            page.getByText(EMPTY_TEXT).waitFor()
            assertThat(cards().count()).isZero()
            Axe.assertNoViolations(page)

            ui.click(buyButton())
            ui.waitForHash("#/comprar")
            ui.heading("Comprar bitcoin").waitFor()
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US5-5 quote unavailable shows its message and a retry without breaking the navigation`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            page.routeProblem(POSITION_ROUTE, "QUOTE_UNAVAILABLE", HTTP_SERVICE_UNAVAILABLE)
            openInvestments(email)

            page.getByText(QUOTE_UNAVAILABLE_TEXT).first().waitFor()
            assertThat(cards().count()).isZero()
            assertThat(hasText("Cotação de referência")).isFalse()
            Axe.assertNoViolations(page)

            page.unroute(POSITION_ROUTE)
            page.routeFixture(POSITION_ROUTE, "me-investments-200.json")
            ui.click(ui.button("Tentar de novo"))
            waitForCards(FIXTURE_CARDS.size)

            ui.click(navItem("Painel"))
            ui.waitForHash("#/painel")
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `US5-6 the list refreshes by itself without moving the focus and the pause stops it`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            page.routeFixture(POSITION_ROUTE, "me-investments-200.json")
            openInvestments(email)
            waitForCards(FIXTURE_CARDS.size)
            val positions = ApiRequests(page, "/me/investments")
            // A focused card is not visible in the mirror; a button is (as in the dashboard E2E).
            ui.button("Atualização automática: ativa").focus()
            val focusedBefore = ui.focused()

            page.waitForTimeout(REFRESH_WAIT_MS)

            assertThat(positions.count).isGreaterThanOrEqualTo(1)
            assertThat(ui.focused()).isEqualTo(focusedBefore)
            assertThat(ui.hash()).isEqualTo("#/investimentos")

            ui.click(ui.button("Atualização automática: ativa"))
            page.waitForFunction(
                "t => document.getElementById('live-polite').textContent === t",
                PAUSED_ANNOUNCEMENT,
            )
            val afterPause = ApiRequests(page, "/me/investments")
            page.waitForTimeout(REFRESH_WAIT_MS)
            assertThat(afterPause.count).isZero()
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `cards at 320 px without horizontal scroll`(browserName: String) {
        val email = registered()
        withPage(browserName, viewport = Viewports.PHONE_SMALL) {
            page.routeFixture(POSITION_ROUTE, "me-investments-200.json")
            openInvestments(email)
            cards().first().waitFor()

            // The LazyColumn composes only the visible cards: the first one, then the last after moving the keyboard
            // focus from the h1 with Tab (pause toggle, then card by card; the list scrolls to the focused card).
            assertThat(cardTexts().first()).isEqualTo(FIXTURE_CARDS.first())
            NoHorizontalScroll.assertOn(page)
            Axe.assertNoViolations(page)
            repeat(MAX_TABS) { if (cardTexts().lastOrNull() != FIXTURE_CARDS.last()) ui.press("Tab") }
            page.waitForCondition { cardTexts().lastOrNull() == FIXTURE_CARDS.last() }
            NoHorizontalScroll.assertOn(page)
            Axe.assertNoViolations(page)
        }
    }

    /** Opens `#/investimentos` without a session: the gate sends to the login, which returns to the page. */
    private fun AppPage.openInvestments(email: String) {
        open("#/investimentos")
        waitForLogin()
        login(email)
        ui.waitForHash("#/investimentos")
        ui.heading("Meus investimentos").first().waitFor()
        page.waitForFunction("t => document.title === t", TITLE)
    }

    /** Each card is one mirror node named by its full sentence (W-14); the shim gives it the `group` role. */
    private fun AppPage.cards(): Locator = page.locator("[aria-label^='Investimento de ']")

    /** The mirror may write the space after "R$" as a no-break space; spaces are normalized before comparing. */
    private fun AppPage.cardTexts(): List<String> =
        cards().all().map { it.getAttribute("aria-label").orEmpty().replace('\u00A0', ' ') }

    private fun AppPage.waitForCards(count: Int) {
        cards().first().waitFor()
        page.waitForCondition { cards().count() == count }
    }

    private fun AppPage.buyButton(): Locator =
        page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Comprar bitcoin").setExact(true)).last()

    /** An item of the navigation; at 1280 px the permanent drawer shows it, named "…, página atual" when current. */
    private fun AppPage.navItem(name: String): Locator =
        page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(name).setExact(true)).first()

    private companion object {
        const val TITLE = "Meus investimentos · Investimento em Bitcoins"
        const val EMPTY_TEXT = "Você não tem investimentos ativos."
        const val REFRESH_WAIT_MS = 15_000.0
        const val MAX_TABS = 8
        val FIXTURE_CARDS =
            listOf(
                "Investimento de 01/11/2026 10:15 (horário de Brasília): valor investido R$ 400,00, " +
                    "cotação na compra R$ 400.000,00, variação +10,00%, alta, valor bruto atual R$ 440,00",
                "Investimento de 03/11/2026 14:30 (horário de Brasília): valor investido R$ 231,58, " +
                    "cotação na compra R$ 463.157,89, variação −5,00%, queda, valor bruto atual R$ 220,00",
                "Investimento de 09/11/2026 08:05 (horário de Brasília): valor investido R$ 44,00, " +
                    "cotação na compra R$ 440.000,00, variação 0,00%, sem variação, valor bruto atual R$ 44,00",
            )
    }
}

package com.kotlinbitcoins.web.e2e.a11y

import com.kotlinbitcoins.web.e2e.auth.PASSWORD
import com.kotlinbitcoins.web.e2e.operations.DEPOSIT_FIELD
import com.kotlinbitcoins.web.e2e.operations.PURCHASE_FIELD
import com.kotlinbitcoins.web.e2e.operations.REDEMPTION_FIELD
import com.kotlinbitcoins.web.e2e.operations.tabToConfirm
import com.kotlinbitcoins.web.e2e.operations.tabToConfirmRedemption
import com.kotlinbitcoins.web.e2e.operations.waitForDepositResult
import com.kotlinbitcoins.web.e2e.operations.waitForPurchaseResult
import com.kotlinbitcoins.web.e2e.operations.waitForRedemptionResult
import com.kotlinbitcoins.web.e2e.operations.waitForRedemptionReview
import com.kotlinbitcoins.web.e2e.operations.waitForReview
import com.kotlinbitcoins.web.e2e.support.AppPage
import com.kotlinbitcoins.web.e2e.support.E2eBase
import com.kotlinbitcoins.web.e2e.support.Screen
import com.kotlinbitcoins.web.e2e.support.Screens
import com.kotlinbitcoins.web.e2e.support.buttonNamed
import com.kotlinbitcoins.web.e2e.support.waitForScreen
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * T220 (FR-048, SC-007, automatable part). Registration, login, logout, deposit, purchase, redemption, investments,
 * statement (filter) and market (table) done with the keyboard only, at 1280 px (permanent drawer before the
 * content). After each step the focus is still in the app (never on `<body>`), an invalid submission focuses the
 * first invalid field, each screen has its own `document.title`, and no accessible name of the mirror carries an
 * English word of a component default (texts from strings.xml are the app's own and are left out).
 *
 * Compose moves the focus inside the canvas and the mirror reflects it only for text fields (W-00), so the menu is
 * reached by its position: the h1 has the focus on arrival (FR-046) and Shift+Tab walks back through "Sair" and the
 * destinations, in the order of contracts/ui-routes.md. Where each Enter went is proved by the screen it opened.
 */
class KeyboardOnlyE2ETest : E2eBase() {
    @ParameterizedTest
    @MethodSource("browsers")
    fun `every flow is done with the keyboard only and the focus is never lost`(browserName: String) {
        val email = api.uniqueEmail()
        withPage(browserName) {
            val flow = Flow(this)
            registrationAndLogin(flow, email)
            operations(flow)
            marketAndStatement(flow)

            assertThat(flow.titles.keys).describedAs("screens visited").containsAll(Screens.ALL.map { it.key })
            assertThat(flow.titles.values.toSet()).describedAs("document.title per screen").hasSize(flow.titles.size)
            assertThat(flow.lostFocus).describedAs("steps that left the focus on <body>").isEmpty()
            assertThat(flow.englishNames).describedAs("English accessible names outside strings.xml").isEmpty()
        }
    }

    private fun AppPage.registrationAndLogin(
        flow: Flow,
        email: String,
    ) {
        // Registration: Enter on the last field submits; the empty form focuses its first invalid field (Nome),
        // then the form is filled by Tab.
        open(Screens.REGISTER.fragment)
        flow.arrive(Screens.REGISTER, pageLoad = true)
        ui.tabTo("Senha")
        ui.press("Enter")
        page.waitForCondition { ui.focused()?.let { it.label == "Nome" && it.isInvalid } == true }
        ui.type("Ana Lima")
        ui.press("Tab")
        ui.type(email)
        ui.press("Tab")
        ui.type(PASSWORD)
        ui.press("Enter")
        flow.arrive(Screens.DASHBOARD)

        // Logout and login.
        flow.navigate(LOGOUT)
        flow.arrive(Screens.LOGIN)
        ui.tabTo("E-mail")
        ui.type(email)
        ui.press("Tab")
        ui.type(PASSWORD)
        ui.press("Enter")
        flow.arrive(Screens.DASHBOARD)
    }

    private fun AppPage.operations(flow: Flow) {
        // Deposit.
        flow.navigate(Screens.DEPOSIT)
        ui.tabTo(DEPOSIT_FIELD)
        ui.type("1000")
        ui.press("Enter")
        waitForDepositResult("R$ 1.000,00")
        flow.assertFocusInApp("deposit result")

        // Purchase: Enter on the amount opens the review; Tab reaches "Confirmar compra".
        flow.navigate(Screens.PURCHASE)
        ui.tabTo(PURCHASE_FIELD)
        ui.type("400")
        ui.press("Enter")
        waitForReview()
        flow.assertFocusInApp("purchase review")
        flow.enterCanvasIfOnBody()
        tabToConfirm()
        ui.press("Enter")
        waitForPurchaseResult()
        flow.assertFocusInApp("purchase result")

        // Investments: the card of the purchase is there.
        flow.navigate(Screens.INVESTMENTS)

        // Redemption.
        flow.navigate(Screens.REDEMPTION)
        ui.tabTo(REDEMPTION_FIELD)
        ui.type("100")
        ui.press("Enter")
        waitForRedemptionReview()
        flow.assertFocusInApp("redemption review")
        flow.enterCanvasIfOnBody()
        tabToConfirmRedemption()
        ui.press("Enter")
        waitForRedemptionResult()
        flow.assertFocusInApp("redemption result")
    }

    private fun AppPage.marketAndStatement(flow: Flow) {
        // Market: Tab from the h1 reaches "Atualizar", then "Ver dados em tabela".
        flow.navigate(Screens.MARKET)
        flow.enterCanvasIfOnBody()
        ui.press("Tab", times = TABS_TO_TABLE_BUTTON)
        ui.press("Enter")
        buttonNamed("Ocultar tabela").waitFor()
        flow.assertFocusInApp("market table")

        // Statement, last: from "Ocultar tabela", Shift+Tab passes "Atualizar" (the h1 left the Tab order when it
        // lost the focus) before the drawer. The filter by Tab and Enter goes to the URL. (Shift+Tab out of a text
        // field comes back to it, L10 of W-00, so no step leaves a text field backwards.)
        flow.navigate(Screens.STATEMENT, controlsBefore = 1)
        ui.tabTo("Data inicial")
        ui.type(today())
        ui.press("Tab")
        ui.type(today())
        ui.press("Enter")
        ui.waitForHash("#/extrato?de=${todayIso()}&ate=${todayIso()}")
    }

    private class Flow(
        private val app: AppPage,
    ) {
        val titles = linkedMapOf<String, String>()
        val lostFocus = mutableListOf<String>()
        val englishNames = sortedSetOf<String>()
        private val page = app.page
        private var current: Screen? = null

        /**
         * Waits for [screen], records its title and checks the focus and the names of its mirror. After a page load
         * the browser starts on `<body>`, as on any page: the focus is checked only after a navigation in the app.
         */
        fun arrive(
            screen: Screen,
            pageLoad: Boolean = false,
        ) {
            app.waitForScreen(screen)
            current = screen
            titles[screen.key] = page.title()
            if (!pageLoad) assertFocusInApp(screen.key)
            englishNames += englishNamesOnPage().map { "${screen.key}: $it" }
        }

        /**
         * From the h1 of a protected screen, Shift+Tab walks back through the drawer ("Sair" first, then the three
         * options of the theme switch) and Enter opens [target]: [LOGOUT] or a destination of the menu.
         * [controlsBefore] counts the content controls before the focused one when the focus is not on the h1.
         */
        fun navigate(
            target: Any,
            controlsBefore: Int = 0,
        ) {
            val stepsBack =
                if (target == LOGOUT) {
                    1
                } else {
                    MENU_ITEMS + THEME_OPTIONS + 1 - Screens.PROTECTED.indexOf(target as Screen)
                }
            // From <body> the first Shift+Tab only enters the canvas, back on the h1 (L8 of W-00).
            val entering = if (focusedTag() == "BODY") 1 else 0
            app.ui.press("Shift+Tab", times = controlsBefore + stepsBack + entering)
            app.ui.press("Enter")
            if (target is Screen) arrive(target)
        }

        /**
         * When the text field that had the focus leaves the screen (e.g. Enter on the amount opens the review), the
         * DOM focus falls to `<body>` while Compose keeps its own focus on the new h1. One Tab enters the canvas
         * again (L8 of W-00).
         */
        fun enterCanvasIfOnBody() {
            if (focusedTag() == "BODY") app.ui.press("Tab")
        }

        fun assertFocusInApp(step: String) {
            val tag = focusedTag()
            if (tag == null || tag == "BODY") lostFocus += "$step (${current?.key})"
        }

        private fun focusedTag(): String? =
            page.evaluate("() => document.activeElement ? document.activeElement.tagName : null") as String?

        @Suppress("UNCHECKED_CAST")
        private fun englishNamesOnPage(): List<String> {
            val names =
                page
                    .locator("[role], [aria-label]")
                    .evaluateAll(
                        "ns => ns.map(n => n.getAttribute('aria-label') || " +
                            "(n.getAttribute('role') ? n.textContent : '') || '')",
                    ) as List<String>
            return names.filter { name ->
                ENGLISH.any { Regex("\\b$it\\b", RegexOption.IGNORE_CASE).containsMatchIn(name) }
            }
        }
    }

    private companion object {
        const val LOGOUT = "Sair"
        const val MENU_ITEMS = 7

        /** The theme switch (Sistema, Claro, Escuro) sits between the destinations and "Sair". */
        const val THEME_OPTIONS = 3
        const val TABS_TO_TABLE_BUTTON = 2
        val BRASILIA: ZoneId = ZoneId.of("America/Sao_Paulo")

        /** Default texts of components in English (T220), minus any word the app itself has in strings.xml. */
        val ENGLISH: List<String> by lazy {
            listOf("Close", "Open", "Navigation", "Dismiss", "Loading", "Show", "Hide", "Back", "Clear", "Selected")
                .filterNot { word -> Regex("\\b$word\\b", RegexOption.IGNORE_CASE).containsMatchIn(appStrings()) }
        }

        /** The values of the `<string>` entries of strings.xml (comments left out). */
        fun appStrings(): String {
            var directory: Path? = Path.of("").toAbsolutePath()
            while (directory != null) {
                val file =
                    listOf("ui", "web/ui")
                        .map { directory.resolve("$it/src/commonMain/composeResources/values/strings.xml") }
                        .firstOrNull(Files::exists)
                if (file != null) {
                    return STRING_VALUE.findAll(Files.readString(file)).joinToString("\n") { it.groupValues[1] }
                }
                directory = directory.parent
            }
            error("strings.xml not found")
        }

        private val STRING_VALUE = Regex("<string[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)

        fun today(): String = LocalDate.now(BRASILIA).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

        fun todayIso(): String = LocalDate.now(BRASILIA).toString()
    }
}

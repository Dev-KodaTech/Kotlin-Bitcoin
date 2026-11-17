package com.kotlinbitcoins.web.e2e.support

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import java.time.Instant
import java.util.regex.Pattern

/**
 * The 9 screens of the app (contracts/ui-routes.md) for the cross-cutting E2E tests of Phase 12: fragment, h1,
 * `document.title`, the name of the menu item (protected screens only) and a [loaded] probe that is true once the
 * data of the screen is on it.
 */
data class Screen(
    val key: String,
    val fragment: String,
    val pageName: String,
    val navLabel: String?,
    val h1: Regex = Regex("^$pageName$"),
    val loaded: (Page) -> Boolean,
) {
    val title: String get() = "$pageName · Investimento em Bitcoins"
    val isProtected: Boolean get() = navLabel != null

    override fun toString(): String = key
}

object Screens {
    private const val LOADING = "Carregando…"

    val LOGIN = Screen("entrar", "#/entrar", "Entrar", null) { it.hasTextbox("E-mail") }
    val REGISTER = Screen("cadastro", "#/cadastro", "Criar conta", null) { it.hasTextbox("Nome") }
    val DASHBOARD =
        Screen("painel", "#/painel", "Painel", "Painel", h1 = Regex("^Olá(, .+|!)$")) {
            it.getByText(Pattern.compile("^Atualizada em ")).count() > 0
        }
    val DEPOSIT = Screen("depositar", "#/depositar", "Depositar", "Depositar") { it.hasTextbox("Valor do depósito") }
    val PURCHASE =
        Screen("comprar", "#/comprar", "Comprar bitcoin", "Comprar") { it.hasTextbox("Valor a investir") }
    val INVESTMENTS =
        Screen("investimentos", "#/investimentos", "Meus investimentos", "Meus investimentos") {
            it.locator("[aria-label^='Investimento de ']").count() > 0
        }
    val REDEMPTION =
        Screen("resgatar", "#/resgatar", "Resgatar", "Resgatar") { it.hasTextbox("Valor a resgatar") }
    val STATEMENT =
        Screen("extrato", "#/extrato", "Extrato", "Extrato") {
            it.locator("[aria-label^='Depósito de '], [aria-label^='Resgate — total ']").count() > 0
        }
    val MARKET =
        Screen("mercado", "#/mercado", "Mercado", "Mercado") {
            it.getByRole(AriaRole.HEADING, Page.GetByRoleOptions().setName("Volume de hoje")).count() > 0
        }

    val PUBLIC = listOf(LOGIN, REGISTER)
    val PROTECTED = listOf(DASHBOARD, DEPOSIT, PURCHASE, INVESTMENTS, REDEMPTION, STATEMENT, MARKET)
    val ALL = PUBLIC + PROTECTED

    /** True while any loading indicator ("Carregando…", text or progress name) is in the mirror. */
    fun isLoading(page: Page): Boolean =
        page.getByText(LOADING).count() > 0 || page.locator("[aria-label='$LOADING']").count() > 0

    private fun Page.hasTextbox(name: String): Boolean =
        getByRole(AriaRole.TEXTBOX, Page.GetByRoleOptions().setName(name)).count() > 0
}

/**
 * An account with data on every screen: a deposit, two purchases at the quote of the stub and a partial redemption
 * (so the statement has a redemption group and the market has volume). Returns the access token.
 */
fun ApiClient.accountWithData(): String {
    val email = uniqueEmail()
    check(register("Ana Lima", email, E2E_PASSWORD).status == HTTP_CREATED) { "register failed" }
    val token = login(email, E2E_PASSWORD)
    check(deposit(token, "1000.00").status == HTTP_CREATED) { "deposit failed" }
    check(purchase(token, "400.00").status == HTTP_CREATED) { "purchase failed" }
    check(purchase(token, "200.00").status == HTTP_CREATED) { "purchase failed" }
    check(redeem(token, "100.00").status == HTTP_CREATED) { "redemption failed" }
    return token
}

/**
 * Writes the session of [token] into the `sessionStorage` of the tab (W-08), the same record the login writes, so a
 * protected screen opens without going through the login UI. Valid for 50 minutes (the API gives 60).
 */
fun AppPage.injectSession(token: String) {
    val expiresAt = Instant.now().plusSeconds(SESSION_SECONDS).toEpochMilli()
    page.evaluate(
        "([t, e]) => sessionStorage.setItem('kb.session', JSON.stringify({ accessToken: t, expiresAtEpochMs: e }))",
        listOf(token, expiresAt.toDouble()),
    )
}

/**
 * Goes to [screen] inside the running app by changing the fragment (the app follows `hashchange`, L9 of W-00) and
 * waits for its h1 and title. With [withData], also waits until its data is shown and nothing is loading.
 */
fun AppPage.goTo(
    screen: Screen,
    withData: Boolean = true,
) {
    if (ui.hash() != screen.fragment) {
        page.evaluate("f => { location.hash = f }", screen.fragment)
    }
    waitForScreen(screen, withData)
}

fun AppPage.waitForScreen(
    screen: Screen,
    withData: Boolean = true,
) {
    ui.waitForHash(screen.fragment)
    page.waitForFunction("t => document.title === t", screen.title)
    h1Of(screen).waitFor()
    if (withData) {
        page.waitForCondition({ screen.loaded(page) && !Screens.isLoading(page) }, WAIT_DATA)
        page.waitForTimeout(SETTLE_MS)
    }
}

/** The h1 of [screen]: the first heading of the mirror, with the name of the screen. */
fun AppPage.h1Of(screen: Screen): Locator =
    page.getByRole(AriaRole.HEADING, Page.GetByRoleOptions().setName(screen.h1.toPattern()).setLevel(1)).first()

/** A button of the shell or of the content by its exact name. */
fun AppPage.buttonNamed(name: String): Locator =
    page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(name).setExact(true))

const val E2E_PASSWORD = "s3nha-forte"
private const val HTTP_CREATED = 201
private const val SESSION_SECONDS = 50L * 60
private const val SETTLE_MS = 300.0
private val WAIT_DATA = Page.WaitForConditionOptions().setTimeout(20_000.0)

package com.kotlinbitcoins.web.e2e.auth

import com.kotlinbitcoins.web.e2e.support.AppPage
import com.microsoft.playwright.Page
import com.microsoft.playwright.Request
import com.microsoft.playwright.options.AriaRole

/** Steps of US1 shared by the auth E2E tests (selectors and keyboard interaction of W-17). */
const val PASSWORD = "s3nha-forte"
const val DASHBOARD_TITLE = "Painel · Investimento em Bitcoins"
const val LOGIN_TITLE = "Entrar · Investimento em Bitcoins"
const val INVALID_CREDENTIALS = "E-mail ou senha incorretos."
const val SESSION_EXPIRED = "Sua sessão expirou. Entre novamente para continuar."
const val ACCOUNT_CREATED = "Conta criada. Entre com seu e-mail e senha."

/** Replaces the content of the text field [label] with [text] (empty clears it). */
fun AppPage.fillField(
    label: String,
    text: String,
) {
    ui.click(ui.textbox(label))
    ui.press("ControlOrMeta+A")
    ui.press("Backspace")
    if (text.isNotEmpty()) ui.type(text)
}

fun AppPage.waitForLogin() {
    ui.heading("Entrar").waitFor()
    page.waitForFunction("t => document.title === t", LOGIN_TITLE)
}

fun AppPage.waitForDashboard() {
    ui.waitForHash("#/painel")
    page.waitForFunction("t => document.title === t", DASHBOARD_TITLE)
}

fun AppPage.register(
    name: String,
    email: String,
    password: String = PASSWORD,
) {
    fillField("Nome", name)
    fillField("E-mail", email)
    fillField("Senha", password)
    ui.click(ui.button("Criar conta"))
}

fun AppPage.login(
    email: String,
    password: String = PASSWORD,
) {
    fillField("E-mail", email)
    fillField("Senha", password)
    ui.click(ui.button("Entrar"))
}

fun AppPage.logout() {
    ui.click(ui.button("Sair"))
    waitForLogin()
}

/** The assertive live region is written on the next frame (DomAnnouncer): wait for it. */
fun AppPage.waitForAnnouncement(text: String) {
    page.waitForFunction("t => document.getElementById('live-assertive').textContent === t", text)
}

/** Visible text anywhere in the accessibility mirror. */
fun AppPage.hasText(text: String): Boolean = page.getByText(text).count() > 0

fun AppPage.headingCount(name: String): Int =
    page.getByRole(AriaRole.HEADING, Page.GetByRoleOptions().setName(name).setExact(true)).count()

/** The token kept by the app for this tab (W-08), or null. */
fun AppPage.storedToken(): String? =
    page.evaluate(
        "() => { const s = sessionStorage.getItem('kb.session'); return s ? JSON.parse(s).accessToken : null; }",
    ) as String?

/** Records the requests of [page] to the API whose path contains [pathPart]. */
class ApiRequests(
    page: Page,
    private val pathPart: String,
) {
    private val seen = mutableListOf<Request>()

    init {
        page.onRequest { if (it.url().contains("/api/v1/") && it.url().contains(pathPart)) seen += it }
    }

    val count: Int get() = seen.size
}

package com.kotlinbitcoins.web.e2e.operations

import com.kotlinbitcoins.web.e2e.auth.PASSWORD
import com.kotlinbitcoins.web.e2e.auth.fillField
import com.kotlinbitcoins.web.e2e.dashboard.QUOTE_ROUTE
import com.kotlinbitcoins.web.e2e.dashboard.openDashboard
import com.kotlinbitcoins.web.e2e.dashboard.shortcut
import com.kotlinbitcoins.web.e2e.support.ApiClient
import com.kotlinbitcoins.web.e2e.support.AppPage
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import com.microsoft.playwright.options.AriaRole
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Steps of US4 shared by the purchase E2E tests. The quote-stub sells at R$ 400.000,00. */
const val PURCHASE_TITLE = "Comprar bitcoin · Investimento em Bitcoins"
const val PURCHASE_REVIEW_TITLE = "Revisar compra · Investimento em Bitcoins"
const val PURCHASE_DONE_TITLE = "Compra realizada · Investimento em Bitcoins"
const val PURCHASE_FIELD = "Valor a investir"
const val PURCHASE_ANNOUNCEMENT = "Compra realizada"
const val ESTIMATE_NOTICE = "Valor estimado. A quantidade final usa a cotação do momento da compra."
const val SATOSHI_WARNING =
    "O valor provavelmente é pequeno demais para comprar a menor fração de bitcoin (0,00000001 BTC)."
const val INVALID_AMOUNT_SATOSHI =
    "$INVALID_AMOUNT O valor também pode ser pequeno demais para comprar 1 satoshi (0,00000001 BTC)."
const val INSUFFICIENT_BALANCE = "Saldo insuficiente para esta compra."
const val QUOTE_UNAVAILABLE_PURCHASE = "Cotação indisponível no momento. Tente novamente em instantes."
const val PURCHASES_ROUTE = "**/api/v1/me/purchases"

/** "Cotação de dd/MM/aaaa hh:mm:ss (horário de Brasília)": the instant of the quote used by the estimate. */
val ESTIMATE_INSTANT = Regex("Cotação de \\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2} \\(horário de Brasília\\)")

/** An account created through the API with [balance] deposited; returns its e-mail. */
fun ApiClient.fundedAccount(balance: String): String =
    registeredAccount().also { email ->
        val response = deposit(login(email, PASSWORD), balance)
        check(response.status == HTTP_CREATED) { "deposit failed: ${response.status}" }
    }

/** Number of purchases in the statement of the account (default period, which includes today). */
fun ApiClient.purchaseCount(email: String): Int {
    val response = statement(login(email, PASSWORD))
    check(response.status == HTTP_OK) { "statement failed: ${response.status}" }
    return Regex("\"type\"\\s*:\\s*\"PURCHASE\"").findAll(response.body).count()
}

/** Logs in, opens the purchase page through the dashboard shortcut and waits for its title. */
fun AppPage.openPurchase(email: String) {
    openDashboard(email)
    ui.click(shortcut("Comprar"))
    waitForPurchaseForm()
}

fun AppPage.waitForPurchaseForm() {
    ui.waitForHash("#/comprar")
    ui.heading("Comprar bitcoin").waitFor()
    page.waitForFunction("t => document.title === t", PURCHASE_TITLE)
}

fun AppPage.typePurchaseAmount(text: String) = fillField(PURCHASE_FIELD, text)

fun AppPage.reviewButton(): Locator = button("Revisar compra")

fun AppPage.confirmButton(): Locator = button("Confirmar compra")

/** Types [amount], asks for the review and waits for it (heading, title suffix and the estimate notice). */
fun AppPage.reviewPurchase(amount: String) {
    typePurchaseAmount(amount)
    ui.click(reviewButton())
    waitForReview()
}

fun AppPage.waitForReview() {
    ui.heading("Revisar compra").waitFor()
    page.waitForFunction("t => document.title === t", PURCHASE_REVIEW_TITLE)
}

/**
 * Moves the keyboard focus of Compose from the review title (focused on arrival, FR-046) to "Confirmar compra":
 * the pause toggle, then the button. The mirror reflects the focus only for text fields (W-00) and Locator.focus()
 * moves only the DOM focus, so the order is pressed, and the test result proves where the Enter keys went.
 */
fun AppPage.tabToConfirm() = ui.press("Tab", times = TABS_FROM_REVIEW_TITLE_TO_CONFIRM)

/** The instant text of the estimate, or null when no estimate is shown. */
fun AppPage.estimateInstantText(): String? {
    val node = page.getByText(ESTIMATE_INSTANT.toPattern()).first()
    return if (node.count() > 0) ESTIMATE_INSTANT.find(node.textContent().orEmpty())?.value else null
}

fun AppPage.waitForEstimateInstant(): String {
    page.getByText(ESTIMATE_INSTANT.toPattern()).first().waitFor()
    return checkNotNull(estimateInstantText())
}

/** Waits for the result: its heading and the title suffix. */
fun AppPage.waitForPurchaseResult() {
    ui.heading("Compra realizada").waitFor()
    page.waitForFunction("t => document.title === t", PURCHASE_DONE_TITLE)
}

/**
 * Waits until the focus is back on the amount field, invalid and described by [message] (the hidden description of
 * the field, W-00).
 */
fun AppPage.waitForPurchaseAmountError(message: String = INVALID_AMOUNT) {
    page.waitForCondition {
        val focused = ui.focused()
        focused?.label == PURCHASE_FIELD && focused.isInvalid && focused.description == message
    }
}

/**
 * Answers `GET /quotes/current` with buy and sell at [price] and the current instant in Brasília, for prices the
 * quote-stub does not give (it always sells at R$ 400.000,00).
 */
fun Page.routeQuote(price: String) {
    route(QUOTE_ROUTE) { route: Route ->
        val now = OffsetDateTime.now(ZoneOffset.ofHours(BRASILIA_OFFSET_HOURS)).format(API_INSTANT)
        route.fulfill(
            Route
                .FulfillOptions()
                .setStatus(HTTP_OK)
                .setContentType("application/json")
                .setBody("""{"buyPrice":"$price","sellPrice":"$price","quotedAt":"$now"}"""),
        )
    }
}

/** A content button; `.last()` skips a menu item with the same name (the content comes after the navigation). */
private fun AppPage.button(name: String): Locator =
    page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(name).setExact(true)).last()

private const val HTTP_OK = 200
private const val HTTP_CREATED = 201
private const val BRASILIA_OFFSET_HOURS = -3
private const val TABS_FROM_REVIEW_TITLE_TO_CONFIRM = 2
private val API_INSTANT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

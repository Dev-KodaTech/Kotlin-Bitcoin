package com.kotlinbitcoins.web.e2e.operations

import com.kotlinbitcoins.web.e2e.auth.PASSWORD
import com.kotlinbitcoins.web.e2e.auth.fillField
import com.kotlinbitcoins.web.e2e.dashboard.openDashboard
import com.kotlinbitcoins.web.e2e.dashboard.shortcut
import com.kotlinbitcoins.web.e2e.support.ApiClient
import com.kotlinbitcoins.web.e2e.support.AppPage
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

/** Steps of US6 shared by the redemption E2E tests. The quote-stub buys and sells at R$ 400.000,00. */
const val REDEMPTION_TITLE = "Resgatar · Investimento em Bitcoins"
const val REDEMPTION_REVIEW_TITLE = "Revisar resgate · Investimento em Bitcoins"
const val REDEMPTION_DONE_TITLE = "Resgate realizado · Investimento em Bitcoins"
const val REDEMPTION_FIELD = "Valor a resgatar"
const val REDEMPTION_ANNOUNCEMENT = "Resgate realizado"
const val REDEMPTION_ESTIMATE_NOTICE = "Valor estimado. A quantidade final usa a cotação do momento do resgate."
const val AVAILABLE_NOTICE = "Valor estimado que muda com a cotação."
const val REDEEM_ALL_WARNING = "Se a cotação cair antes da confirmação, o valor pode ficar acima do disponível."
const val REDEMPTION_EXCEEDS = "O valor solicitado é maior que o disponível para resgate."
const val REINVESTMENT_EXPLANATION =
    "O BTC que sobrou do investimento parcialmente resgatado continua investido, com a cotação e a data da compra " +
        "original."
const val REDEMPTION_EMPTY = "Você não tem investimentos para resgatar."
const val QUOTE_UNAVAILABLE_REDEMPTION = "Cotação indisponível no momento. Tente novamente em instantes."
const val REDEMPTIONS_ROUTE = "**/api/v1/me/redemptions"

/**
 * An account created through the API with [deposit] deposited and one purchase of each of [purchases], in order (the
 * first is the oldest, liquidated first by the FIFO of the API); returns its e-mail.
 */
fun ApiClient.investedAccount(
    deposit: String,
    vararg purchases: String,
): String =
    fundedAccount(deposit).also { email ->
        val token = login(email, PASSWORD)
        purchases.forEach { amount ->
            val response = purchase(token, amount)
            check(response.status == HTTP_CREATED) { "purchase failed: ${response.status}" }
        }
    }

/**
 * Number of redemptions in the statement of the account (default period, which includes today): one redemption has
 * one REDEMPTION entry per liquidated investment, all with its `requestId`.
 */
fun ApiClient.redemptionCount(email: String): Int {
    val response = statement(login(email, PASSWORD))
    check(response.status == HTTP_OK) { "statement failed: ${response.status}" }
    return REDEMPTION_ENTRY
        .findAll(response.body)
        .map { it.groupValues[1] }
        .toSet()
        .size
}

/** Logs in, opens the redemption page through the dashboard shortcut and waits for its title. */
fun AppPage.openRedemption(email: String) {
    openDashboard(email)
    ui.click(shortcut("Resgatar"))
    waitForRedemptionForm()
}

fun AppPage.waitForRedemptionForm() {
    ui.waitForHash("#/resgatar")
    ui.heading("Resgatar").waitFor()
    page.waitForFunction("t => document.title === t", REDEMPTION_TITLE)
}

fun AppPage.typeRedemptionAmount(text: String) = fillField(REDEMPTION_FIELD, text)

fun AppPage.redemptionReviewButton(): Locator = contentButton("Revisar resgate")

fun AppPage.redemptionConfirmButton(): Locator = contentButton("Confirmar resgate")

fun AppPage.redeemAllButton(): Locator = contentButton("Resgatar tudo")

fun AppPage.editAmountButton(): Locator = contentButton("Alterar valor")

/** Types [amount], asks for the review and waits for it (heading and title suffix). */
fun AppPage.reviewRedemption(amount: String) {
    typeRedemptionAmount(amount)
    ui.click(redemptionReviewButton())
    waitForRedemptionReview()
}

fun AppPage.waitForRedemptionReview() {
    ui.heading("Revisar resgate").waitFor()
    page.waitForFunction("t => document.title === t", REDEMPTION_REVIEW_TITLE)
}

/**
 * Moves the keyboard focus of Compose from the review title (focused on arrival, FR-046) to "Confirmar resgate", the
 * next focusable element (the pause toggle is above, next to the available). See [tabToConfirm].
 */
fun AppPage.tabToConfirmRedemption() = ui.press("Tab", times = TABS_FROM_REVIEW_TITLE_TO_CONFIRM)

/** Waits for the result: its heading and the title suffix. */
fun AppPage.waitForRedemptionResult() {
    ui.heading("Resgate realizado").waitFor()
    page.waitForFunction("t => document.title === t", REDEMPTION_DONE_TITLE)
}

/** Waits until the focus is back on the amount field, invalid and described by [message] (W-00). */
fun AppPage.waitForRedemptionAmountError(message: String = INVALID_AMOUNT) {
    page.waitForCondition {
        val focused = ui.focused()
        focused?.label == REDEMPTION_FIELD && focused.isInvalid && focused.description == message
    }
}

/** The liquidations of the result: one mirror node each, named by its full sentence ("Liquidação Total: …"). */
fun AppPage.liquidations(): List<String> =
    page
        .locator("[aria-label^='Liquidação ']")
        .all()
        .map { it.getAttribute("aria-label").orEmpty().replace(' ', ' ') }

/** A content button; `.last()` skips a menu item with the same name (the content comes after the navigation). */
private fun AppPage.contentButton(name: String): Locator =
    page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(name).setExact(true)).last()

private val REDEMPTION_ENTRY = Regex("\"type\"\\s*:\\s*\"REDEMPTION\"[^}]*?\"requestId\"\\s*:\\s*\"([^\"]+)\"")
private const val HTTP_OK = 200
private const val HTTP_CREATED = 201
private const val TABS_FROM_REVIEW_TITLE_TO_CONFIRM = 1

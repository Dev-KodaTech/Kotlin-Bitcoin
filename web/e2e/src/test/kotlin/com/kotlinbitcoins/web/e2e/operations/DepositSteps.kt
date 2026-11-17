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

/** Steps of US3 shared by the deposit E2E tests. */
const val DEPOSIT_TITLE = "Depositar · Investimento em Bitcoins"
const val DEPOSIT_DONE_TITLE = "Depósito realizado · Investimento em Bitcoins"
const val DEPOSIT_FIELD = "Valor do depósito"
const val DEPOSIT_ANNOUNCEMENT = "Depósito realizado"
const val INVALID_AMOUNT = "Informe um valor maior que zero, com no máximo 2 casas decimais."
const val UNCERTAIN = "Não foi possível confirmar se a operação foi concluída."
const val DEPOSITS_ROUTE = "**/api/v1/me/deposits"

/** An account created through the API; returns its e-mail. */
fun ApiClient.registeredAccount(name: String = "Maria Souza"): String =
    uniqueEmail().also { register(name, it, PASSWORD) }

/** Number of deposits in the statement of the account (default period, which includes today). */
fun ApiClient.depositCount(email: String): Int {
    val response = statement(login(email, PASSWORD))
    check(response.status == HTTP_OK) { "statement failed: ${response.status}" }
    return Regex("\"type\"\\s*:\\s*\"DEPOSIT\"").findAll(response.body).count()
}

/** Logs in, opens the deposit page through the dashboard shortcut and waits for its title. */
fun AppPage.openDeposit(email: String) {
    openDashboard(email)
    ui.click(shortcut("Depositar"))
    waitForDepositForm()
}

fun AppPage.waitForDepositForm() {
    ui.waitForHash("#/depositar")
    ui.heading("Depositar").waitFor()
    page.waitForFunction("t => document.title === t", DEPOSIT_TITLE)
}

/**
 * The submit button "Depositar". On this page the menu item is named "Depositar, página atual"; `.last()` keeps the
 * content button even if the menu item ever has the same name (the content comes after the navigation).
 */
fun AppPage.depositButton(): Locator =
    page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Depositar").setExact(true)).last()

fun AppPage.typeAmount(text: String) = fillField(DEPOSIT_FIELD, text)

/**
 * Waits until the focus is back on the amount field, invalid and described by the INVALID_AMOUNT message. The
 * message lives in the hidden description of the field (`aria-describedby`), as for every field error (W-00).
 */
fun AppPage.waitForAmountError() {
    page.waitForCondition {
        val focused = ui.focused()
        focused?.label == DEPOSIT_FIELD && focused.isInvalid && focused.description == INVALID_AMOUNT
    }
}

/** Waits for the result: its heading, the title suffix and the assertive announcement. */
fun AppPage.waitForDepositResult(formattedAmount: String) {
    ui.heading("Depósito de $formattedAmount realizado").waitFor()
    page.waitForFunction("t => document.title === t", DEPOSIT_DONE_TITLE)
}

private const val HTTP_OK = 200

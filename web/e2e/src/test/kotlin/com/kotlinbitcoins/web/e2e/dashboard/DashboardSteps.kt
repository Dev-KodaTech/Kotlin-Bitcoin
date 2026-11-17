package com.kotlinbitcoins.web.e2e.dashboard

import com.kotlinbitcoins.web.e2e.auth.login
import com.kotlinbitcoins.web.e2e.auth.waitForDashboard
import com.kotlinbitcoins.web.e2e.auth.waitForLogin
import com.kotlinbitcoins.web.e2e.support.AppPage
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import com.microsoft.playwright.options.AriaRole
import java.nio.file.Files
import java.nio.file.Path

/** Steps of US2 shared by the dashboard E2E tests. */
const val QUOTE_UNAVAILABLE_TEXT = "Cotação indisponível no momento"
const val DEPENDS_ON_QUOTE = "Depende da cotação"
const val NO_INVESTMENTS = "Você ainda não tem investimentos. Faça sua primeira compra."
const val PAUSED_ANNOUNCEMENT = "Atualização automática pausada"
const val QUOTE_ROUTE = "**/api/v1/quotes/current"
const val POSITION_ROUTE = "**/api/v1/me/investments"
const val HTTP_SERVICE_UNAVAILABLE = 503
const val HTTP_INTERNAL_ERROR = 500

/** Opens the login, enters with [email] and waits for the dashboard. */
fun AppPage.openDashboard(email: String) {
    open("#/entrar")
    waitForLogin()
    login(email)
    waitForDashboard()
}

/**
 * A shortcut button of the dashboard. The navigation of the shell may have an item with the same name, so the
 * shortcut is the last one in the reading order (the content comes after the navigation).
 */
fun AppPage.shortcut(name: String): Locator =
    page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(name).setExact(true)).last()

/** "Atualizada em dd/MM/aaaa hh:mm:ss (horário de Brasília)" of the quote block. */
private val QUOTE_INSTANT = Regex("Atualizada em \\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2} \\(horário de Brasília\\)")

/**
 * The instant text of the quote block, or null when no quote is shown. The mirror may group the lines of the block
 * in one text node, so the instant is taken from the text of that node.
 */
fun AppPage.quoteInstantText(): String? {
    val node = page.getByText(QUOTE_INSTANT.toPattern()).first()
    return if (node.count() > 0) QUOTE_INSTANT.find(node.textContent().orEmpty())?.value else null
}

/** Waits until a quote is shown and returns its instant text. */
fun AppPage.waitForQuoteInstant(): String {
    page.getByText(QUOTE_INSTANT.toPattern()).first().waitFor()
    return checkNotNull(quoteInstantText())
}

/** Answers every request matching [glob] with the Problem fixture [code] (web/contract-fixtures/problem). */
fun Page.routeProblem(
    glob: String,
    code: String,
    status: Int,
) {
    val body = Files.readString(fixturesDir().resolve("problem/$code.json"))
    route(glob) { route: Route ->
        route.fulfill(
            Route
                .FulfillOptions()
                .setStatus(status)
                .setContentType("application/problem+json")
                .setBody(body),
        )
    }
}

/** Answers every request matching [glob] with the success fixture [name] (web/contract-fixtures/success). */
fun Page.routeFixture(
    glob: String,
    name: String,
    status: Int = HTTP_OK,
) {
    val body = Files.readString(fixturesDir().resolve("success/$name"))
    route(glob) { route: Route ->
        route.fulfill(
            Route
                .FulfillOptions()
                .setStatus(status)
                .setContentType("application/json")
                .setBody(body),
        )
    }
}

private const val HTTP_OK = 200

/** web/contract-fixtures, found by walking up from the working directory. */
internal fun fixturesDir(): Path {
    var directory: Path? = Path.of("").toAbsolutePath()
    while (directory != null) {
        val candidate = directory.resolve("contract-fixtures")
        if (Files.isDirectory(candidate)) return candidate
        val nested = directory.resolve("web/contract-fixtures")
        if (Files.isDirectory(nested)) return nested
        directory = directory.parent
    }
    error("web/contract-fixtures not found")
}

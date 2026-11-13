package com.kotlinbitcoins.web.e2e.support

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.WaitForSelectorState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

/**
 * Base of the E2E tests (W-17): one Playwright browser per name of `e2e.browsers`, a fresh context per test with
 * locale pt-BR and the time zone America/New_York (to prove that dates are shown in Brasília, FR-037), and every CSP
 * violation (event or console) failing the test (W-15). Run against `docker compose --profile stub up` with
 * `./gradlew :e2e:e2eTest`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class E2eBase {
    protected val baseUrl: String = System.getProperty("e2e.baseUrl", "http://localhost:8081").trimEnd('/')
    protected val api = ApiClient()
    private val browserNames =
        System
            .getProperty("e2e.browsers", "chromium,firefox,webkit")
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
    private lateinit var playwright: Playwright
    private val browsers = mutableMapOf<String, Browser>()

    @BeforeAll
    fun startPlaywright() {
        playwright = Playwright.create()
    }

    @AfterAll
    fun stopPlaywright() {
        browsers.values.forEach(Browser::close)
        playwright.close()
    }

    /** Source of `@MethodSource("browsers")`. */
    fun browsers(): List<String> = browserNames

    protected fun <T> withPage(
        browserName: String,
        viewport: Viewport = Viewports.DESKTOP,
        prepare: (BrowserContext) -> Unit = {},
        block: AppPage.() -> T,
    ): T {
        val context =
            browser(browserName).newContext(
                Browser
                    .NewContextOptions()
                    .setViewportSize(viewport.width, viewport.height)
                    .setDeviceScaleFactor(viewport.deviceScaleFactor)
                    .setLocale("pt-BR")
                    .setTimezoneId("America/New_York"),
            )
        return context.use {
            prepare(it)
            val appPage = AppPage(it.newPage(), baseUrl)
            val result = appPage.block()
            assertThat(appPage.cspViolations()).describedAs("CSP violations (W-15)").isEmpty()
            result
        }
    }

    private fun browser(name: String): Browser =
        browsers.getOrPut(name) {
            val type: BrowserType =
                when (name) {
                    "chromium" -> playwright.chromium()
                    "firefox" -> playwright.firefox()
                    "webkit" -> playwright.webkit()
                    else -> error("Unknown browser '$name'")
                }
            type.launch()
        }
}

/** A page of the app with the CSP collector installed before any script runs. */
class AppPage(
    val page: Page,
    private val baseUrl: String,
) {
    val ui = Ui(page)
    private val consoleCspMessages = mutableListOf<String>()

    init {
        page.addInitScript(
            "window.__cspViolations = [];" +
                "document.addEventListener('securitypolicyviolation', e => " +
                "window.__cspViolations.push(e.violatedDirective + ' ' + e.blockedURI));",
        )
        page.onConsoleMessage { message ->
            val text = message.text()
            if (text.contains("Content Security Policy") || text.contains("Content-Security-Policy")) {
                consoleCspMessages += text
            }
        }
    }

    /** Opens the app at [fragment] (e.g. "#/extrato") and waits until the Wasm app replaced the loading screen. */
    fun open(fragment: String = "") {
        page.navigate("$baseUrl/$fragment")
        waitForApp()
    }

    fun waitForApp() {
        val detached = Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED).setTimeout(APP_START_TIMEOUT_MS)
        page.locator("#boot").waitFor(detached)
    }

    @Suppress("UNCHECKED_CAST")
    fun cspViolations(): List<String> =
        (page.evaluate("() => window.__cspViolations || []") as List<String>) + consoleCspMessages

    private companion object {
        const val APP_START_TIMEOUT_MS = 30_000.0
    }
}

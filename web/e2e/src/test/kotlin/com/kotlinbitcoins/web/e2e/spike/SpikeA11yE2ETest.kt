package com.kotlinbitcoins.web.e2e.spike

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/**
 * Accessibility go/no-go spike (research.md W-00, task T021), run against `:spike-a11y:wasmJsBrowserDevelopmentRun`:
 *
 *     ./gradlew :e2e:e2eTest -PbaseUrl=http://localhost:8082 --tests '*SpikeA11y*'
 *
 * Self-contained (does not use the E2E support of T101). Evidence (accessibility snapshots, axe results, notes) goes to
 * web/e2e/build/reports/spike-a11y/<browser>/. Screens are opened with the spike's mitigations on (default); the raw
 * Compose mirror (`?mitigacoes=0`) is audited too, for comparison only.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpikeA11yE2ETest {
    private val baseUrl = System.getProperty("e2e.baseUrl", "http://localhost:8082").trimEnd('/')
    private val browserNames =
        System
            .getProperty("e2e.browsers", "chromium,firefox,webkit")
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
    private val reportRoot = Path.of("build", "reports", "spike-a11y")
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

    fun browsers(): List<String> = browserNames

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

    private fun <T> withPage(
        browserName: String,
        viewport: Pair<Int, Int> = SpikePage.DESKTOP,
        deviceScaleFactor: Double = 1.0,
        block: SpikePage.() -> T,
    ): T =
        SpikePage(browser(browserName), baseUrl, reportRoot.resolve(browserName), viewport, deviceScaleFactor).use {
            val result = it.block()
            assertThat(it.cspViolations()).describedAs("CSP violations (W-15)").isEmpty()
            result
        }

    // A10 + A12 + evidence for A3/A7/A8: axe on every screen and state, with the accessibility snapshot saved.
    @ParameterizedTest
    @MethodSource("browsers")
    fun `axe finds no WCAG 2_1 A or AA violation on the three screens`(browserName: String) {
        val violations =
            withPage(browserName) {
                open("/login", "Entrar")
                assertThat(page.locator("html").getAttribute("lang")).isEqualTo("pt-BR")
                val login = audit("login")
                click(button("Entrar").last())
                val loginError = audit("login-erro")
                open("/form", "Criar conta")
                val form = audit("formulario")
                click(button("Cadastrar"))
                val formErrors = audit("formulario-erros")
                open("/tabela", "Extrato")
                val cards = audit("tabela-cartoes")
                click(button("Tabela (cabeçalhos e células)"))
                // Variant (a) is audited for the report only: W-14 picks the variant from this result.
                write("tabela-variante-a.violacoes.txt", audit("tabela-variante-a").joinToString("\n"))
                login + loginError + form + formErrors + cards
            }
        withPage(browserName) {
            open("/form", "Criar conta", query = "?mitigacoes=0")
            click(button("Cadastrar"))
            write("sem-mitigacoes.violacoes.txt", audit("formulario-erros-sem-mitigacoes").joinToString("\n"))
        }
        assertThat(violations).isEmpty()
    }

    // A1 + A3 (selectors) + A4 + A5 + A11: keyboard-only login and registration.
    @ParameterizedTest
    @MethodSource("browsers")
    fun `role and label selectors reach every control`(browserName: String) {
        withPage(browserName) {
            open("/login", "Entrar")
            listOf(textbox("E-mail"), textbox("Senha"), button("Entrar").last(), page.getByLabel("E-mail"))
                .forEach { assertThat(it.count()).isEqualTo(1) }
            assertThat(button("Criar conta").count()).isEqualTo(1)
            assertThat(button("Extrato").count()).isEqualTo(1)
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `login works with the keyboard only and the error goes to the alert region`(browserName: String) {
        withPage(browserName) {
            open("/login", "Entrar")
            val tabsToEmail = tabTo("E-mail")
            page.keyboard().type("ana@exemplo.com")
            press("Tab")
            assertThat(focused()?.label).isEqualTo("Senha")
            page.keyboard().type("senha-errada")
            press("Enter")
            assertThat(liveRegionText("live-assertive")).isEqualTo("E-mail ou senha incorretos.")

            // Leaving the last text field: record where the DOM focus goes, then activate "Entrar" with the keyboard.
            page.evaluate("() => document.getElementById('live-assertive').textContent = ''")
            press("Tab")
            val afterLastField = focused()
            press("Tab")
            press("Enter")
            val announcedByButton = liveRegionText("live-assertive")
            write(
                "teclado-login.txt",
                "Tabs até o e-mail: $tabsToEmail\nFoco após Tab no último campo: $afterLastField\n" +
                    "Enter no botão (2º Tab) anunciou: '$announcedByButton'\n",
            )
            assertThat(announcedByButton).isEqualTo("E-mail ou senha incorretos.")
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `invalid submit moves the focus to the first invalid field with its error`(browserName: String) {
        withPage(browserName) {
            open("/form", "Criar conta")
            tabTo("Valor do primeiro depósito (R$)")
            assertThat(focused()?.inputMode).describedAs("decimal keyboard (A11)").isEqualTo("decimal")
            press("Tab", times = 2)
            press("Enter")

            val first = focused()
            assertThat(first?.label).isEqualTo("Nome")
            assertThat(first?.isInvalid).isTrue()
            assertThat(first?.description).isEqualTo("Informe o nome.")
            press("Tab")
            assertThat(focused()?.description).isEqualTo("Informe um e-mail válido, como nome@exemplo.com.")
        }
        withPage(browserName) {
            open("/form", "Criar conta", query = "?mitigacoes=0")
            click(button("Cadastrar"))
            write("foco-sem-mitigacoes.txt", "Campo focado após envio inválido, sem mitigações: ${focused()}\n")
        }
    }

    // A6: one announcement per state change through the DOM region; the periodic counter is never in a live region.
    @ParameterizedTest
    @MethodSource("browsers")
    fun `quote availability is announced once per change and the periodic update is not`(browserName: String) {
        withPage(browserName) {
            open("/tabela", "Extrato")
            val toggle = button("Alternar cotação (técnica B)")
            click(toggle)
            assertThat(liveRegionText("live-polite")).isEqualTo("Cotação indisponível no momento")
            click(toggle)
            assertThat(liveRegionText("live-polite")).isEqualTo("Cotação restabelecida")
            assertThat(mirrorLiveRegions()).describedAs("aria-live inside the Compose mirror").isEmpty()

            click(button("Alternar cotação (técnica A)"))
            write("live-region-tecnica-a.txt", "aria-live no espelho após técnica A: ${mirrorLiveRegions()}\n")
        }
        withPage(browserName) {
            open("/tabela", "Extrato", query = "?mitigacoes=0")
            write("live-region-sem-mitigacoes.txt", "aria-live no espelho sem mitigações: ${mirrorLiveRegions()}\n")
        }
    }

    // W-04 + A8: fragment routes with a parameter survive back, forward and reload; title changes per screen.
    @ParameterizedTest
    @MethodSource("browsers")
    fun `back, forward and reload keep the route and its parameter`(browserName: String) {
        withPage(browserName) {
            open("/tabela?filtro=x", "Extrato")
            assertFilter("x")
            assertThat(title()).isEqualTo("Extrato · Investimento em Bitcoins")
            assertThat(page.getByText("Sonda de compatibilidade: 0.00100000 BTC · 09/11/2026 08:10").count())
                .describedAs("bignum + America/Sao_Paulo at runtime (T014)")
                .isEqualTo(1)

            click(button("Criar conta"))
            heading("Criar conta").waitFor()
            assertThat(hash()).isEqualTo("#/form")
            assertThat(title()).isEqualTo("Criar conta · Investimento em Bitcoins")

            page.goBack()
            heading("Extrato").waitFor()
            assertFilter("x")
            page.goForward()
            heading("Criar conta").waitFor()
            assertThat(hash()).isEqualTo("#/form")
            page.goBack()
            heading("Extrato").waitFor()

            page.reload()
            heading("Extrato").waitFor()
            assertFilter("x")

            click(textbox("Filtro"))
            page.waitForFunction(DEEP_FOCUS_LABEL_IS, "Filtro")
            page.keyboard().press("End")
            page.keyboard().type("y")
            screenshot("filtro-xy.png")
            click(button("Aplicar"))
            assertFilter("xy")
            page.goBack()
            heading("Extrato").waitFor()
            assertFilter("x")

            // Observation for W-04: a fragment typed in the address bar (same document) is not applied by the binding.
            page.evaluate("() => { location.hash = '#/form' }")
            page.waitForTimeout(TYPED_HASH_WAIT_MS)
            val applied = heading("Criar conta").count() == 1
            write("hash-digitado.txt", "Hash alterado no mesmo documento (#/form) trocou de tela: $applied\n")
        }
    }

    // A9: 320 px wide and 1280 px at 200% (640 × 400 CSS px, deviceScaleFactor 2) without horizontal scroll.
    @ParameterizedTest
    @MethodSource("browsers")
    fun `no content is lost at 320 px nor at 200 percent zoom`(browserName: String) {
        val screens = listOf("/login" to "Entrar", "/form" to "Criar conta", "/tabela" to "Extrato")
        val problems =
            listOf(Triple(320 to 640, 1.0, "320px"), Triple(640 to 400, 2.0, "zoom200"))
                .flatMap { (viewport, scale, label) ->
                    withPage(browserName, viewport, scale) {
                        screens.flatMap { (fragment, heading) ->
                            open(fragment, heading)
                            val file = "$label${fragment.replace('/', '-')}.png"
                            screenshot(file)
                            overflowingNodes().map { "$label $fragment: $it" }
                        }
                    }
                }
        assertThat(problems).isEmpty()
    }

    private fun SpikePage.assertFilter(expected: String) {
        waitForHash("#/tabela?filtro=$expected")
        page.getByText("Filtro aplicado: $expected").waitFor(Locator.WaitForOptions().setTimeout(FILTER_TIMEOUT_MS))
    }

    private companion object {
        const val FILTER_TIMEOUT_MS = 5_000.0
        const val TYPED_HASH_WAIT_MS = 1_500.0
        const val DEEP_FOCUS_LABEL_IS =
            "label => { let a = document.activeElement; " +
                "while (a && a.shadowRoot && a.shadowRoot.activeElement) a = a.shadowRoot.activeElement; " +
                "return a && a.getAttribute('aria-label') === label; }"
    }
}

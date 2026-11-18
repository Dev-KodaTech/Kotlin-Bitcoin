package com.kotlinbitcoins.web.e2e.smoke

import com.kotlinbitcoins.web.e2e.support.Axe
import com.kotlinbitcoins.web.e2e.support.E2eBase
import com.kotlinbitcoins.web.e2e.support.NoHorizontalScroll
import com.kotlinbitcoins.web.e2e.support.Viewports
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitUntilState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Smoke test of the delivered shell (T102): static loading screen, page metadata, delivery headers of the nginx
 * (W-15), same-origin API proxy, CSP and the message for browsers without WasmGC (W-16).
 */
class AppShellE2ETest : E2eBase() {
    private val http = HttpClient.newHttpClient()

    @ParameterizedTest
    @MethodSource("browsers")
    fun `boot screen first, then the app with title, language and no accessibility violation`(browserName: String) {
        withPage(browserName) {
            page.navigate(baseUrl, Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED))
            assertThat(page.locator("#boot").isVisible).describedAs("#boot before the Wasm app").isTrue()
            assertThat(page.locator("#boot-message").textContent()).isEqualTo("Carregando…")

            waitForApp()
            // No session: the gate sends the dashboard request to the login page.
            ui.heading("Entrar").waitFor()
            assertThat(page.title()).isEqualTo("Entrar · Investimento em Bitcoins")
            assertThat(page.locator("html").getAttribute("lang")).isEqualTo("pt-BR")
            assertThat(ui.hash()).startsWith("#/entrar")
            // The buttons of the mirror get their name a moment after the heading: axe runs on the settled page.
            ui.button("Entrar").waitFor()
            ui.button("Criar conta").waitFor()
            Axe.assertNoViolations(page)
            NoHorizontalScroll.assertOn(page)
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `wasm files are served as application wasm`(browserName: String) {
        val wasmTypes = mutableMapOf<String, String?>()
        withPage(browserName, Viewports.PHONE) {
            page.onResponse { response ->
                if (response.url().endsWith(".wasm")) wasmTypes[response.url()] = response.headerValue("content-type")
            }
            open()
        }
        assertThat(wasmTypes).isNotEmpty()
        assertThat(wasmTypes.values).allMatch { it == "application/wasm" }
    }

    @Test
    fun `hashed wasm is brotli compressed and immutable, index html is revalidated and has the CSP`() {
        val index = get("/", "br")
        assertThat(index.statusCode()).isEqualTo(HTTP_OK)
        assertThat(index.headers().firstValue("cache-control")).hasValue("no-cache")
        assertThat(index.headers().firstValue("content-security-policy").orElse(""))
            .contains("'wasm-unsafe-eval'")
            .contains("frame-ancestors 'none'")
        assertThat(index.headers().firstValue("x-content-type-options")).hasValue("nosniff")
        assertThat(index.headers().firstValue("referrer-policy")).hasValue("no-referrer")

        // Read uncompressed: the response above may be brotli.
        val html = String(get("/", "identity").body())
        val wasmFiles =
            Regex("href=\"([0-9a-f]{16,}\\.wasm)\"")
                .findAll(html)
                .map {
                    it.groupValues[1]
                }.toList()
        assertThat(wasmFiles).describedAs("preload links injected by inject-preload.sh").isNotEmpty()
        wasmFiles.forEach { file ->
            val wasm = get("/$file", "br")
            assertThat(wasm.statusCode()).isEqualTo(HTTP_OK)
            assertThat(wasm.headers().firstValue("content-type")).hasValue("application/wasm")
            assertThat(wasm.headers().firstValue("content-encoding")).hasValue("br")
            assertThat(wasm.headers().firstValue("cache-control").orElse("")).contains("immutable")
        }
        assertThat(get("/app.js", "br").headers().firstValue("cache-control")).hasValue("no-cache")
    }

    @Test
    fun `the API is reached through the same origin`() {
        assertThat(api.get("/quotes/current").status).isEqualTo(HTTP_UNAUTHORIZED)
        assertThat(get("/api/v1/quotes/current", "identity").statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `a browser without WasmGC gets the pt-BR message and app js is never requested`(browserName: String) {
        val requested = mutableListOf<String>()
        withPage(browserName) {
            page.addInitScript("WebAssembly.validate = () => false;")
            page.onRequest { requested += it.url() }
            page.navigate(baseUrl)
            page.waitForFunction(
                "() => document.getElementById('boot-message').textContent.startsWith('Seu navegador')",
            )
            assertThat(page.locator("#boot-message").textContent())
                .isEqualTo("Seu navegador não é compatível. Atualize para a versão mais recente.")
            page.waitForTimeout(NO_REQUEST_WAIT_MS)
        }
        assertThat(requested).noneMatch { it.endsWith("/app.js") }
    }

    private fun get(
        path: String,
        acceptEncoding: String,
    ): HttpResponse<ByteArray> =
        http.send(
            HttpRequest
                .newBuilder(URI.create(baseUrl + path))
                .header("Accept-Encoding", acceptEncoding)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val NO_REQUEST_WAIT_MS = 1_000.0
    }
}

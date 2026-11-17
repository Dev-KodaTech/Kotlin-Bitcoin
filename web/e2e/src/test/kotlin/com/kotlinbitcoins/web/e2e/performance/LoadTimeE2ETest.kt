package com.kotlinbitcoins.web.e2e.performance

import com.google.gson.JsonObject
import com.kotlinbitcoins.web.e2e.support.AppPage
import com.kotlinbitcoins.web.e2e.support.E2eBase
import com.kotlinbitcoins.web.e2e.support.Screen
import com.kotlinbitcoins.web.e2e.support.Screens
import com.kotlinbitcoins.web.e2e.support.accountWithData
import com.kotlinbitcoins.web.e2e.support.h1Of
import com.kotlinbitcoins.web.e2e.support.waitForScreen
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitUntilState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import kotlin.math.ceil

/**
 * T222 (SC-010), Chromium only (CDP `Network.emulateNetworkConditions`) with the "4G típico" profile of research
 * W-15: 9,000 kbps down, 1,500 kbps up, 60 ms RTT.
 * (a) With the app loaded, 20 screen openings: content or loading in ≤ 1 s and data in ≤ 3 s (p95).
 * (b) 5 cold loads (a new context each, so no cache): `#boot` painted in ≤ 1 s (first contentful paint) and the
 * dashboard usable, with its data, in ≤ 6 s.
 * Bundle: the brotli sizes of the `*.wasm`, `*.js` and font files of the first load must add up to ≤ 4.2 MB; the
 * br/gz sizes are printed for research.md W-15.
 */
class LoadTimeE2ETest : E2eBase() {
    private lateinit var token: String

    @BeforeAll
    fun onlyChromium() {
        assumeTrue("chromium" in browsers(), "CDP network emulation exists only in Chromium")
        token = api.accountWithData()
    }

    @Test
    fun `a with the app loaded each screen shows content in 1 s and data in 3 s at p95`() {
        val contentMs = mutableListOf<Double>()
        val dataMs = mutableListOf<Double>()
        withPage(CHROMIUM, prepare = ::withSession) {
            throttle()
            open(Screens.DASHBOARD.fragment)
            waitForScreen(Screens.DASHBOARD)
            val targets = Screens.PROTECTED.filter { it != Screens.DASHBOARD } + Screens.DASHBOARD
            repeat(OPENINGS) { index ->
                val screen = targets[index % targets.size]
                val start =
                    page.evaluate("f => { const t = performance.now(); location.hash = f; return t }", screen.fragment)
                        as Number
                page.waitForCondition { h1Of(screen).count() > 0 || Screens.isLoading(page) }
                contentMs += now() - start.toDouble()
                waitForData(screen)
                dataMs += now() - start.toDouble()
            }
        }
        println("LOAD (a) content ms: $contentMs")
        println("LOAD (a) data ms: $dataMs")
        assertThat(p95(contentMs)).describedAs("p95 content or loading (ms)").isLessThanOrEqualTo(CONTENT_BUDGET_MS)
        assertThat(p95(dataMs)).describedAs("p95 data (ms)").isLessThanOrEqualTo(DATA_BUDGET_MS)
    }

    @Test
    fun `b a cold load paints the boot screen in 1 s and the dashboard is usable in 6 s`() {
        val bootMs = mutableListOf<Double>()
        val usableMs = mutableListOf<Double>()
        val firstLoad = linkedSetOf<String>()
        repeat(COLD_LOADS) {
            withPage(CHROMIUM, prepare = ::withSession) {
                throttle()
                page.onResponse { response ->
                    if (BUNDLE_FILE.containsMatchIn(URI.create(response.url()).path)) firstLoad += response.url()
                }
                page.navigate(
                    "$baseUrl/${Screens.DASHBOARD.fragment}",
                    Page.NavigateOptions().setWaitUntil(WaitUntilState.COMMIT),
                )
                waitForData(Screens.DASHBOARD, timeoutMs = COLD_TIMEOUT_MS)
                usableMs += now()
                bootMs +=
                    (
                        page.evaluate(
                            "() => (performance.getEntriesByName('first-contentful-paint')[0] || {}).startTime || -1",
                        ) as Number
                    ).toDouble()
            }
        }
        println("LOAD (b) boot ms: $bootMs")
        println("LOAD (b) usable ms: $usableMs")
        val sizes = bundleSizes(firstLoad)
        sizes.forEach { (file, size) -> println("BUNDLE $file br=${size.first} gz=${size.second}") }
        val brotli = sizes.values.sumOf { it.first }
        println("BUNDLE total br=$brotli gz=${sizes.values.sumOf { it.second }}")

        assertThat(bootMs).describedAs("first contentful paint of #boot (ms)").allMatch { it in 0.0..BOOT_BUDGET_MS }
        assertThat(usableMs).describedAs("dashboard usable (ms)").allMatch { it <= USABLE_BUDGET_MS }
        assertThat(brotli).describedAs("brotli bytes of *.wasm, *.js and fonts of the first load").isLessThanOrEqualTo(
            BUNDLE_BUDGET_BYTES,
        )
    }

    private fun withSession(context: BrowserContext) {
        val expiresAt = Instant.now().plusSeconds(SESSION_SECONDS).toEpochMilli()
        context.addInitScript(
            "if (!sessionStorage.getItem('kb.session')) sessionStorage.setItem('kb.session', " +
                "JSON.stringify({ accessToken: '$token', expiresAtEpochMs: $expiresAt }));",
        )
    }

    private fun AppPage.throttle() {
        val cdp = page.context().newCDPSession(page)
        cdp.send("Network.enable")
        cdp.send(
            "Network.emulateNetworkConditions",
            JsonObject().apply {
                addProperty("offline", false)
                addProperty("latency", RTT_MS)
                addProperty("downloadThroughput", DOWN_KBPS * BYTES_PER_KBIT)
                addProperty("uploadThroughput", UP_KBPS * BYTES_PER_KBIT)
            },
        )
    }

    private fun AppPage.waitForData(
        screen: Screen,
        timeoutMs: Double = DATA_TIMEOUT_MS,
    ) {
        page.waitForCondition(
            { h1Of(screen).count() > 0 && screen.loaded(page) && !Screens.isLoading(page) },
            Page.WaitForConditionOptions().setTimeout(timeoutMs),
        )
    }

    private fun AppPage.now(): Double = (page.evaluate("() => performance.now()") as Number).toDouble()

    /** Brotli and gzip sizes of each file, as the nginx of the client serves them. */
    private fun bundleSizes(urls: Collection<String>): Map<String, Pair<Long, Long>> {
        val http = HttpClient.newHttpClient()

        fun size(
            url: String,
            encoding: String,
        ): Long =
            http
                .send(
                    HttpRequest
                        .newBuilder(URI.create(url))
                        .header("Accept-Encoding", encoding)
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofByteArray(),
                ).body()
                .size
                .toLong()
        return urls.associate { url -> URI.create(url).path to (size(url, "br") to size(url, "gzip")) }
    }

    private fun p95(values: List<Double>): Double = values.sorted()[ceil(values.size * P95).toInt() - 1]

    private companion object {
        const val CHROMIUM = "chromium"
        const val OPENINGS = 20
        const val COLD_LOADS = 5
        const val RTT_MS = 60
        const val DOWN_KBPS = 9_000
        const val UP_KBPS = 1_500

        /** CDP throughput is in bytes per second: 1 kbit = 125 bytes. */
        const val BYTES_PER_KBIT = 125
        const val CONTENT_BUDGET_MS = 1_000.0
        const val DATA_BUDGET_MS = 3_000.0
        const val BOOT_BUDGET_MS = 1_000.0
        const val USABLE_BUDGET_MS = 6_000.0
        const val DATA_TIMEOUT_MS = 15_000.0
        const val COLD_TIMEOUT_MS = 30_000.0

        /** 4.2 MB (W-15, revised in Phase 12: Skiko alone is 2.62 MB and the usable time is within 6 s). */
        const val BUNDLE_BUDGET_BYTES = 4_200_000L
        const val P95 = 0.95
        const val SESSION_SECONDS = 50L * 60
        val BUNDLE_FILE = Regex("\\.(wasm|js|mjs|ttf|otf|woff2?)$")
    }
}

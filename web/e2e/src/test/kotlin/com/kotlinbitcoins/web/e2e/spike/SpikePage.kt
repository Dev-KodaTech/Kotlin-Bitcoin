package com.kotlinbitcoins.web.e2e.spike

import com.deque.html.axecore.playwright.AxeBuilder
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import java.nio.file.Files
import java.nio.file.Path

/** Deep focus: Compose keeps DOM focus inside its shadow root (canvas or the hidden `.compose-backing-field`). */
data class FocusedElement(
    val tag: String,
    val label: String?,
    val isInvalid: Boolean,
    val description: String?,
    val inputMode: String?,
)

/**
 * One page of the spike (T021) with the evidence collectors: CSP violations (event + console), accessibility
 * snapshots and axe results, written to build/reports/spike-a11y/<browser>/.
 */
class SpikePage(
    browser: Browser,
    private val baseUrl: String,
    private val reportDir: Path,
    viewport: Pair<Int, Int> = DESKTOP,
    deviceScaleFactor: Double = 1.0,
) : AutoCloseable {
    private val context =
        browser.newContext(
            Browser
                .NewContextOptions()
                .setViewportSize(viewport.first, viewport.second)
                .setDeviceScaleFactor(deviceScaleFactor)
                .setLocale("pt-BR"),
        )
    val page: Page = context.newPage()
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
        Files.createDirectories(reportDir)
    }

    /** Opens `#fragment` (with an optional query string before it) and waits for the screen heading. */
    fun open(
        fragment: String,
        heading: String,
        query: String = "",
    ) {
        // A fresh document each time: the spike reads the fragment only at start-up (W-04).
        page.navigate("about:blank")
        page.navigate("$baseUrl/$query#$fragment")
        heading(heading).waitFor()
    }

    fun heading(name: String): Locator = page.getByRole(AriaRole.HEADING, Page.GetByRoleOptions().setName(name))

    fun button(name: String): Locator =
        page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(name).setExact(true))

    fun textbox(name: String): Locator = page.getByRole(AriaRole.TEXTBOX, Page.GetByRoleOptions().setName(name))

    /** Pointer click on the mirror node: the canvas above it receives the event at the same coordinates. */
    fun click(locator: Locator) {
        locator.click(Locator.ClickOptions().setForce(true))
        page.waitForTimeout(SETTLE_MS)
    }

    fun press(
        key: String,
        times: Int = 1,
    ) {
        repeat(times) {
            page.keyboard().press(key)
            page.waitForTimeout(KEY_SETTLE_MS)
        }
    }

    /** Presses Tab until the focused element is the text field named [label]. */
    fun tabTo(
        label: String,
        maxPresses: Int = 12,
    ): Int {
        repeat(maxPresses) { presses ->
            if (focused()?.label == label) return presses
            press("Tab")
        }
        val reached = focused()
        check(reached?.label == label) { "Field '$label' not reached with $maxPresses Tab presses; focus=$reached" }
        return maxPresses
    }

    @Suppress("UNCHECKED_CAST")
    fun focused(): FocusedElement? {
        val raw =
            page.evaluate(
                """() => {
                let a = document.activeElement;
                while (a && a.shadowRoot && a.shadowRoot.activeElement) a = a.shadowRoot.activeElement;
                if (!a) return null;
                const id = a.getAttribute('aria-describedby');
                const described = id ? a.getRootNode().getElementById(id) : null;
                return { tag: a.tagName, label: a.getAttribute('aria-label'),
                         invalid: a.getAttribute('aria-invalid') === 'true',
                         description: described ? described.textContent : null,
                         inputMode: a.getAttribute('inputmode') };
            }""",
            ) as Map<String, Any?>? ?: return null
        return FocusedElement(
            tag = raw["tag"] as String,
            label = raw["label"] as String?,
            isInvalid = raw["invalid"] as Boolean,
            description = raw["description"] as String?,
            inputMode = raw["inputMode"] as String?,
        )
    }

    fun liveRegionText(id: String): String = page.locator("#$id").textContent().orEmpty()

    /** Elements under the Compose shadow root that carry `aria-live` (FR-047: periodic updates must not). */
    fun mirrorLiveRegions(): List<String> =
        page.locator("#cmp_a11y_root, #cmp_a11y_root [aria-live]").evaluateAll(
            "nodes => nodes.filter(n => n.hasAttribute('aria-live'))" +
                ".map(n => (n.id || n.tagName) + ' aria-live=' + n.getAttribute('aria-live'))",
        ) as List<String>

    fun hash(): String = page.evaluate("() => location.hash") as String

    /** Waits until the fragment equals [expected] (navigation is applied asynchronously by the NavController). */
    fun waitForHash(expected: String) {
        page.waitForFunction("expected => location.hash === expected", expected)
    }

    fun title(): String = page.title()

    /** axe with the WCAG 2.1 A/AA tags; saves violations and the accessibility snapshot as evidence. */
    fun audit(name: String): List<String> {
        val results = AxeBuilder(page).withTags(WCAG_TAGS).analyze()
        val violations =
            results.violations.map { rule ->
                "${rule.id} (${rule.impact}): ${rule.help} — ${rule.nodes.size} nó(s): " +
                    rule.nodes.take(MAX_NODES_IN_REPORT).joinToString("; ") { it.target.toString() }
            }
        Files.writeString(reportDir.resolve("$name.aria.yml"), page.locator("body").ariaSnapshot())
        Files.writeString(
            reportDir.resolve("$name.axe.txt"),
            if (violations.isEmpty()) "0 violações\n" else violations.joinToString("\n", postfix = "\n"),
        )
        return violations
    }

    @Suppress("UNCHECKED_CAST")
    fun cspViolations(): List<String> =
        (page.evaluate("() => window.__cspViolations || []") as List<String>) + consoleCspMessages

    /** Page has no horizontal scroll and every mirror control/heading lies inside the viewport (A9). */
    @Suppress("UNCHECKED_CAST")
    fun overflowingNodes(): List<String> {
        val width = checkNotNull(page.viewportSize()).width
        val pageScroll = (page.evaluate(PAGE_SCROLL_SCRIPT) as Number).toInt()
        val outside =
            page
                .locator("[role=button], [role=textbox], [role=heading]")
                .evaluateAll(OUTSIDE_VIEWPORT_SCRIPT, width) as List<String>
        val scroll = if (pageScroll > 0) listOf("documento rola ${pageScroll}px na horizontal") else emptyList()
        return scroll + outside
    }

    /**
     * Screenshot as evidence. In WebKit, Playwright injects a `<style>` to take it, which the CSP blocks; violations
     * raised while capturing are discarded, so only the application's own violations count.
     */
    fun screenshot(file: String) {
        val before = consoleCspMessages.size
        val eventsBefore = (page.evaluate("() => (window.__cspViolations || []).length") as Number).toInt()
        page.screenshot(Page.ScreenshotOptions().setPath(reportDir.resolve(file)))
        page.waitForTimeout(KEY_SETTLE_MS)
        page.evaluate("n => { window.__cspViolations.length = n }", eventsBefore)
        while (consoleCspMessages.size > before) consoleCspMessages.removeAt(consoleCspMessages.lastIndex)
    }

    fun write(
        name: String,
        content: String,
    ) {
        Files.writeString(reportDir.resolve(name), content)
    }

    override fun close() = context.close()

    companion object {
        val DESKTOP = 1280 to 800
        val WCAG_TAGS = listOf("wcag2a", "wcag2aa", "wcag21a", "wcag21aa")
        private const val SETTLE_MS = 600.0
        private const val KEY_SETTLE_MS = 150.0
        private const val MAX_NODES_IN_REPORT = 5
        private const val PAGE_SCROLL_SCRIPT =
            "() => document.documentElement.scrollWidth - document.documentElement.clientWidth"
        private const val OUTSIDE_VIEWPORT_SCRIPT =
            "(nodes, width) => nodes" +
                ".map(n => [n.textContent || n.getAttribute('aria-label'), n.getBoundingClientRect()])" +
                ".filter(([, r]) => r.width > 0 && (r.left < -1 || r.right > width + 1))" +
                ".map(([t, r]) => t + ' [' + Math.round(r.left) + ', ' + Math.round(r.right) + ']')"
    }
}

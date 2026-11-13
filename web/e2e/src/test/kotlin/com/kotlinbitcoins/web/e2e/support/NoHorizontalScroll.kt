package com.kotlinbitcoins.web.e2e.support

import com.microsoft.playwright.Page
import org.assertj.core.api.Assertions.assertThat

/**
 * FR-044 / A9: the document does not scroll horizontally, and every control and heading of the accessibility mirror
 * lies inside the viewport (the canvas itself always fits, so the mirror is what reveals clipped content).
 */
object NoHorizontalScroll {
    @Suppress("UNCHECKED_CAST")
    fun assertOn(page: Page) {
        val width = checkNotNull(page.viewportSize()).width
        val documentOverflow =
            (
                page.evaluate(
                    "() => document.documentElement.scrollWidth - document.documentElement.clientWidth",
                ) as Number
            ).toInt()
        val outside =
            page
                .locator("[role=button], [role=textbox], [role=heading], [role=link]")
                .evaluateAll(
                    "(nodes, width) => nodes" +
                        ".map(n => [n.textContent || n.getAttribute('aria-label'), n.getBoundingClientRect()])" +
                        ".filter(([, r]) => r.width > 0 && (r.left < -1 || r.right > width + 1))" +
                        ".map(([t, r]) => t + ' [' + Math.round(r.left) + ', ' + Math.round(r.right) + ']')",
                    width,
                ) as List<String>
        assertThat(documentOverflow).describedAs("horizontal scroll of the document (px)").isLessThanOrEqualTo(0)
        assertThat(outside).describedAs("mirror nodes outside the viewport").isEmpty()
    }
}

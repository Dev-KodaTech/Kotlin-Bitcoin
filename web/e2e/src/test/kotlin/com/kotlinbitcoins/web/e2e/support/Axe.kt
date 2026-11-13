package com.kotlinbitcoins.web.e2e.support

import com.deque.html.axecore.playwright.AxeBuilder
import com.microsoft.playwright.Page
import org.assertj.core.api.Assertions.assertThat

/**
 * axe-core over the accessibility mirror of the Compose canvas (W-17). It does not see what the canvas draws
 * (contrast, visible focus): those are covered by ThemeContrastTest and the manual checklist (quickstart §6).
 */
object Axe {
    val WCAG_TAGS = listOf("wcag2a", "wcag2aa", "wcag21a", "wcag21aa")

    fun assertNoViolations(page: Page) {
        val violations =
            AxeBuilder(page).withTags(WCAG_TAGS).analyze().violations.map { rule ->
                "${rule.id} (${rule.impact}): ${rule.help} — " +
                    rule.nodes.take(MAX_NODES).joinToString("; ") { it.target.toString() }
            }
        assertThat(violations).describedAs("axe WCAG 2.1 A/AA violations").isEmpty()
    }

    private const val MAX_NODES = 5
}

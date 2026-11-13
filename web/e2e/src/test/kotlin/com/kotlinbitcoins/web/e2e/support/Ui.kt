package com.kotlinbitcoins.web.e2e.support

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

/** Element that has the DOM focus, looking through the shadow roots of the Compose canvas. */
data class FocusedElement(
    val tag: String,
    val label: String?,
    val isInvalid: Boolean,
    val description: String?,
    val inputMode: String?,
)

/**
 * Selectors and keyboard interaction decided by the accessibility gate ("W-00 Resultado", W-17): `getByRole` and
 * `getByLabel` cross the open shadow root of the mirror; a pointer click is a forced click on the mirror node (the
 * canvas receives it at the same coordinates); focus is read from the deepest active element. While a text field is
 * focused, M2 gives the real input the same name as the mirror node, so text boxes are taken with `.first()`.
 */
class Ui(
    val page: Page,
) {
    fun heading(name: String): Locator = page.getByRole(AriaRole.HEADING, Page.GetByRoleOptions().setName(name))

    fun button(name: String): Locator =
        page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(name).setExact(true))

    fun textbox(name: String): Locator = page.getByRole(AriaRole.TEXTBOX, Page.GetByRoleOptions().setName(name)).first()

    fun click(locator: Locator) {
        locator.click(Locator.ClickOptions().setForce(true))
        page.waitForTimeout(SETTLE_MS)
    }

    fun type(text: String) {
        page.keyboard().type(text)
        page.waitForTimeout(KEY_SETTLE_MS)
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

    /** Presses Tab until the focused text field is the one named [label]. */
    fun tabTo(
        label: String,
        maxPresses: Int = MAX_TAB_PRESSES,
    ) {
        repeat(maxPresses) {
            if (focused()?.label == label) return
            press("Tab")
        }
        check(
            focused()?.label == label,
        ) { "Field '$label' not reached with $maxPresses Tab presses; focus=${focused()}" }
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

    fun hash(): String = page.evaluate("() => location.hash") as String

    /** The NavController applies navigation asynchronously; wait for the fragment. */
    fun waitForHash(expected: String) {
        page.waitForFunction("expected => location.hash === expected", expected)
    }

    private companion object {
        const val SETTLE_MS = 600.0
        const val KEY_SETTLE_MS = 150.0
        const val MAX_TAB_PRESSES = 15
    }
}

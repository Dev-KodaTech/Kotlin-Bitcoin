package com.kotlinbitcoins.web.spike

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/*
 * Mitigations found by the spike for gaps of the Compose 1.12.2 accessibility mirror (research.md "W-00 Resultado").
 * They depend on internals of the mirror (`#cmp_a11y_root`, `.compose-backing-field`), so any CMP update MUST rerun
 * SpikeA11yE2ETest.
 *
 * M1 (mirror): the label slot of a text field is not exposed; `contentDescription` becomes `aria-label`.
 * M2 (focus): the focused DOM element of a text field is a hidden `<input class="compose-backing-field">` with no name
 *     and no error state. A `focusin` listener on the shadow root copies the label and the error of the Compose field
 *     that owns the focus (aria-label, aria-invalid, aria-describedby to a hidden node inside the same shadow root).
 * M3 (live): the mirror root carries `aria-live="polite"`, so any text change on screen (e.g. a 10 s refresh) may be
 *     announced. The attribute is removed and kept removed; announcements go through the DOM regions (W-09).
 * M4 (structure): axe flags ARIA that the mirror generates: `role="heading"` without `aria-level`, `<canvas
 *     role="generic">` (abstract role), `role="list"` without `listitem` children and `aria-label` on role-less
 *     `div`s. The observer fixes them: first heading = level 1, others = 2; canvas role removed; list children become
 *     `listitem` (a LazyColumn that also holds headings or controls loses the list role); other labelled role-less
 *     nodes become `group`.
 */

@JsFun(
    """() => {
    const container = document.getElementById('compose-root');
    const errorId = 'kb-field-error';
    const apply = (input) => {
        const field = globalThis.__kbFocusedField;
        if (!field || !input || !input.classList || !input.classList.contains('compose-backing-field')) return;
        input.setAttribute('aria-label', field.label);
        const root = input.getRootNode();
        let error = root.getElementById(errorId);
        if (!error) {
            error = document.createElement('span');
            error.id = errorId;
            error.hidden = true;
            input.parentNode.appendChild(error);
        }
        if (field.error) {
            error.textContent = field.error;
            input.setAttribute('aria-invalid', 'true');
            input.setAttribute('aria-describedby', errorId);
        } else {
            error.textContent = '';
            input.removeAttribute('aria-invalid');
            input.removeAttribute('aria-describedby');
        }
    };
    globalThis.__kbApplyFocusedField = () => {
        const host = container.querySelector(':scope > div > div');
        if (host && host.shadowRoot) apply(host.shadowRoot.activeElement);
    };
    const install = () => {
        const host = container.querySelector(':scope > div > div');
        if (!host || !host.shadowRoot) { requestAnimationFrame(install); return; }
        const shadow = host.shadowRoot;
        shadow.addEventListener('focusin', (event) => apply(event.target), true);
        const repair = () => {
            const mirror = shadow.getElementById('cmp_a11y_root');
            if (!mirror) return;
            if (mirror.hasAttribute('aria-live')) mirror.removeAttribute('aria-live');
            const canvas = shadow.querySelector('canvas[role]');
            if (canvas) canvas.removeAttribute('role');
            mirror.querySelectorAll('[role=heading]').forEach((heading, index) => {
                const level = index === 0 ? '1' : '2';
                if (heading.getAttribute('aria-level') !== level) heading.setAttribute('aria-level', level);
            });
            mirror.querySelectorAll('[role=list]').forEach((list) => {
                // A LazyColumn that also holds headings or controls is a scroll container, not a list.
                if (list.querySelector(':scope > [role]:not([role=listitem])')) {
                    list.removeAttribute('role');
                } else {
                    list.querySelectorAll(':scope > :not([role])').forEach((item) => item.setAttribute('role', 'listitem'));
                }
            });
            mirror.querySelectorAll('div[aria-label]:not([role])').forEach((node) => node.setAttribute('role', 'group'));
        };
        new MutationObserver(repair).observe(shadow, {
            subtree: true, childList: true, attributes: true, attributeFilter: ['aria-live', 'role', 'aria-level'],
        });
        repair();
    };
    install();
}""",
)
external fun installComposeA11yShim()

@JsFun(
    """(label, error) => {
    globalThis.__kbFocusedField = { label: label, error: error };
    globalThis.__kbApplyFocusedField && globalThis.__kbApplyFocusedField();
}""",
)
private external fun setFocusedField(
    label: String,
    error: String?,
)

/** M1 + M2 for one text field: name in the mirror and name/error on the focused backing input. */
fun Modifier.accessibleField(
    label: String,
    error: String?,
): Modifier =
    composed {
        var isFocused by remember { mutableStateOf(false) }
        LaunchedEffect(isFocused, error) {
            if (isFocused) setFocusedField(label, error)
        }
        this
            .semantics { contentDescription = label }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) setFocusedField(label, error)
            }
    }

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.kotlinbitcoins.web.app

import com.kotlinbitcoins.web.ui.components.FieldAccessibility

/*
 * Mitigations M2–M4 of the accessibility gate (research.md "W-00 Resultado", decision GO of T023), carried over from
 * the spike. They depend on internals of the Compose 1.12.2 mirror (`#cmp_a11y_root`, `.compose-backing-field`), so
 * any CMP update MUST rerun the E2E with axe before going in.
 *
 * M2 (focus): the focused DOM element of a text field is a hidden `<input class="compose-backing-field">` with no name
 *     and no error state; a `focusin` listener copies the label and the error reported by :ui (accessibleField).
 * M3 (live): the mirror root carries `aria-live="polite"`, so any text change (e.g. a 10 s refresh) may be announced.
 *     The attribute is removed and kept removed; announcements go through #live-polite/#live-assertive (W-09).
 * M4 (structure): first heading = level 1, others = 2; the canvas loses its abstract role; list children become
 *     `listitem` (a LazyColumn that also holds headings or controls loses the list role); other labelled role-less
 *     nodes become `group`.
 * M5 (focus kept): when an action leaves a text field (e.g. Enter submits and the screen changes), its backing
 *     `<input>` goes away and the DOM focus falls to `<body>`, while Compose already moved its own focus (e.g. to the
 *     new h1, FR-046). The canvas gets the DOM focus back, keeping the Compose focus: on `focusout` (Chromium) or when
 *     the focused field leaves the DOM (Firefox and WebKit fire no `focusout` then). Tab and Shift+Tab are left to
 *     the browser (L8/L10 of W-00).
 * M1 (name of the field in the mirror) is done in :ui by `contentDescription`.
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
        const isField = (node) => !!(node && node.classList && node.classList.contains('compose-backing-field'));
        let lastTabAt = -Infinity;
        let focusedField = null;
        const refocusCanvas = () => {
            if (performance.now() - lastTabAt < 250) return;
            requestAnimationFrame(() => {
                const active = document.activeElement;
                const canvas = shadow.querySelector('canvas');
                if ((!active || active === document.body) && canvas) canvas.focus({ preventScroll: true });
            });
        };
        shadow.addEventListener('focusin', (event) => {
            focusedField = isField(event.target) ? event.target : null;
            apply(event.target);
        }, true);
        shadow.addEventListener('keydown', (event) => { if (event.key === 'Tab') lastTabAt = performance.now(); }, true);
        // Chromium fires focusout when the focused field is removed; Firefox and WebKit do not, hence the observer.
        shadow.addEventListener('focusout', (event) => {
            if (!isField(event.target) || event.relatedTarget) return;
            refocusCanvas();
        }, true);
        new MutationObserver(() => {
            if (focusedField && !focusedField.isConnected) {
                focusedField = null;
                refocusCanvas();
            }
        }).observe(shadow, { subtree: true, childList: true });
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
internal external fun installComposeA11yShim()

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

/** Browser side of M2: receives the focused field from :ui. */
internal object DomFieldAccessibility : FieldAccessibility {
    override fun onFieldFocused(
        label: String,
        error: String?,
    ) = setFocusedField(label, error)
}

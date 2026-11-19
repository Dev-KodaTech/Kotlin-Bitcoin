@file:OptIn(ExperimentalWasmJsInterop::class)
// The parameters below are read by the JavaScript code inside js(...), which detekt cannot see.
@file:Suppress("UnusedParameter")

package com.kotlinbitcoins.web.data.browser

/*
 * Bindings of the theme choice (MediaPreferences) to `localStorage` and to `<html data-theme>` (W-09): the only
 * value of the app kept there, never the session (FR-008). Errors are contained by [guarded] in the adapter.
 */

internal fun localStorageGet(key: String): String? = js("window.localStorage.getItem(key)")

internal fun localStorageSet(
    key: String,
    value: String,
): Unit = js("{ window.localStorage.setItem(key, value); }")

internal fun localStorageRemove(key: String): Unit = js("{ window.localStorage.removeItem(key); }")

/** A change of [key] in `localStorage` made by another tab (the `storage` event never fires in the writing tab). */
internal fun onLocalStorageChange(
    key: String,
    handler: (String?) -> Unit,
): Unit =
    js(
        """{
        window.addEventListener('storage', (event) => {
            if (event.storageArea === window.localStorage && (event.key === key || event.key === null)) {
                handler(event.newValue);
            }
        });
    }""",
    )

/** `data-theme` on `<html>`: read by loading.css, so a reload paints the boot screen in the chosen theme. */
internal fun setDocumentTheme(value: String?): Unit =
    js(
        """{
        if (value) document.documentElement.setAttribute('data-theme', value);
        else document.documentElement.removeAttribute('data-theme');
    }""",
    )

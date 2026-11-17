@file:OptIn(ExperimentalWasmJsInterop::class)
// The parameters below are read by the JavaScript code inside js(...), which detekt cannot see.
@file:Suppress("UnusedParameter", "UnusedPrivateProperty")

package com.kotlinbitcoins.web.data.browser

/*
 * The only bindings of :data to browser APIs (W-09), without kotlinx-browser. Each function is a single JS call so
 * that the adapters stay thin; errors are contained by [guarded] in the adapters.
 */

internal fun sessionStorageGet(key: String): String? = js("window.sessionStorage.getItem(key)")

internal fun sessionStorageSet(
    key: String,
    value: String,
): Unit = js("{ window.sessionStorage.setItem(key, value); }")

internal fun sessionStorageRemove(key: String): Unit = js("{ window.sessionStorage.removeItem(key); }")

internal external class BroadcastChannel(
    name: String,
) : JsAny {
    fun postMessage(message: String)

    fun close()
}

internal fun addMessageListener(
    channel: BroadcastChannel,
    handler: (String) -> Unit,
): JsAny =
    js(
        """{
        const listener = (event) => { if (typeof event.data === 'string') handler(event.data); };
        channel.addEventListener('message', listener);
        return listener;
    }""",
    )

internal fun removeMessageListener(
    channel: BroadcastChannel,
    listener: JsAny,
): Unit = js("{ channel.removeEventListener('message', listener); }")

internal fun isDocumentVisible(): Boolean = js("document.visibilityState === 'visible'")

internal fun onVisibilityChange(handler: () -> Unit): Unit =
    js("{ document.addEventListener('visibilitychange', () => handler()); }")

internal fun mediaMatches(query: String): Boolean = js("window.matchMedia(query).matches")

internal fun onMediaChange(
    query: String,
    handler: (Boolean) -> Unit,
): Unit = js("{ window.matchMedia(query).addEventListener('change', (event) => handler(event.matches)); }")

internal fun setDocumentTitle(text: String): Unit = js("{ document.title = text; }")

internal fun announceInRegion(
    regionId: String,
    text: String,
): Unit =
    js(
        """{
        const region = document.getElementById(regionId);
        if (!region) return;
        region.textContent = '';
        requestAnimationFrame(() => { region.textContent = text; });
    }""",
    )

@file:OptIn(ExperimentalWasmJsInterop::class)
// The parameters below are read by the JavaScript code inside js(...), which detekt cannot see.
@file:Suppress("UnusedParameter")

package com.kotlinbitcoins.web.app

// Browser bindings of the edge module (W-09), without kotlinx-browser.

internal fun locationOrigin(): String = js("window.location.origin")

internal fun locationHash(): String = js("window.location.hash")

internal fun onHashChange(handler: () -> Unit): Unit = js("{ window.addEventListener('hashchange', () => handler()); }")

/** Adds a history entry with [fragment] ("#/…"); fires neither `hashchange` nor `popstate`. */
internal fun historyPush(fragment: String): Unit = js("window.history.pushState(null, '', fragment)")

/** Replaces the current history entry with [fragment]; fires neither `hashchange` nor `popstate`. */
internal fun historyReplace(fragment: String): Unit = js("window.history.replaceState(null, '', fragment)")

/** `pagehide` that is not entering the back/forward cache: the page is really going away. */
internal fun onPageUnload(handler: () -> Unit): Unit =
    js("{ window.addEventListener('pagehide', (event) => { if (!event.persisted) handler(); }); }")

/** Removes the static loading screen of index.html (W-16). */
internal fun removeBootScreen(): Unit = js("{ const boot = document.getElementById('boot'); if (boot) boot.remove(); }")

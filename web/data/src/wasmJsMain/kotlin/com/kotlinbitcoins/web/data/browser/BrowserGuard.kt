package com.kotlinbitcoins.web.data.browser

/**
 * Exception boundary of the browser adapters (Principle III): a browser API that throws (blocked storage, malformed
 * data) yields [fallback] instead of crossing into :state.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> guarded(
    fallback: T,
    block: () -> T,
): T =
    try {
        block()
    } catch (_: Throwable) {
        fallback
    }

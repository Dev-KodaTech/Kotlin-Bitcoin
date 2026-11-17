@file:OptIn(ExperimentalWasmJsInterop::class)

package com.kotlinbitcoins.web.app

/** Named time zones for kotlinx-datetime on wasmJs (W-07); referenced from main() so it stays in the bundle. */
@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule

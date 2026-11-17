@file:OptIn(ExperimentalWasmJsInterop::class)

package com.kotlinbitcoins.web.core.time

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule

/** W-07: fails if the named time zone data (@js-joda/timezone) stops being loaded. */
class BrasiliaTimeZoneDataTest {
    @Test
    @Suppress("UNUSED_EXPRESSION")
    fun americaSaoPauloIsAvailable() {
        // Mandatory reference: loads the named time zone data.
        JsJodaTimeZoneModule
        assertEquals("America/Sao_Paulo", TimeZone.of("America/Sao_Paulo").id)
    }
}

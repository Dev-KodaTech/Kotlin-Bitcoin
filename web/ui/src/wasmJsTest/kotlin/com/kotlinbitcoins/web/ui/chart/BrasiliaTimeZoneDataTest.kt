@file:OptIn(ExperimentalWasmJsInterop::class)

package com.kotlinbitcoins.web.ui.chart

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule

/** W-07: the chart geometry tests need America/Sao_Paulo (@js-joda/timezone). */
class BrasiliaTimeZoneDataTest {
    @Test
    @Suppress("UNUSED_EXPRESSION")
    fun americaSaoPauloIsAvailable() {
        // Mandatory reference: loads the named time zone data.
        JsJodaTimeZoneModule
        assertEquals("America/Sao_Paulo", TimeZone.of("America/Sao_Paulo").id)
    }
}

package com.kotlinbitcoins.web.core.money

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.support.failureError
import com.kotlinbitcoins.web.core.support.successValue
import kotlin.test.Test
import kotlin.test.assertEquals

class VariationPercentTest {
    private fun variation(raw: String): VariationPercent = VariationPercent.parseApi(raw).successValue()

    @Test
    fun `parseApi accepts only the contract pattern`() {
        listOf("10.00", "-5.00", "0.00", "1234.56").forEach { raw -> variation(raw) }
        listOf("10", "10.0", "+10.00", "1e2", "", "10,00", "--1.00").forEach { raw ->
            assertEquals(ApiError.Unexpected, VariationPercent.parseApi(raw).failureError(), "\"$raw\" must be refused")
        }
    }

    @Test
    fun `positive variation`() {
        val value = variation("10.00")
        assertEquals("+10,00%", value.format())
        assertEquals(Trend.Up, value.trend)
    }

    @Test
    fun `negative variation uses the minus sign`() {
        val value = variation("-5.00")
        assertEquals("−5,00%", value.format())
        assertEquals(Trend.Down, value.trend)
    }

    @Test
    fun `zero variation has no sign`() {
        assertEquals("0,00%", variation("0.00").format())
        assertEquals(Trend.Flat, variation("0.00").trend)
        assertEquals(Trend.Flat, variation("-0.00").trend)
        assertEquals("0,00%", variation("-0.00").format())
    }

    @Test
    fun `large values are grouped`() {
        assertEquals("+1.234,56%", variation("1234.56").format())
    }
}

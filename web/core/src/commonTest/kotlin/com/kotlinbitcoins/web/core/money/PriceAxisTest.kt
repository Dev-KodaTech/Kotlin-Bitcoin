package com.kotlinbitcoins.web.core.money

import com.kotlinbitcoins.web.core.support.successValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PriceAxisTest {
    private fun quote(raw: String): QuotePrice = QuotePrice.parseApi(raw).successValue()

    private fun marks(
        min: String,
        max: String,
    ): List<String>? = PriceAxis.marks(quote(min), quote(max))?.map { it.toPlainString() }

    @Test
    fun `marks are round multiples of one step around the range, at most six`() {
        assertEquals(
            listOf("397000.00", "398000.00", "399000.00", "400000.00", "401000.00", "402000.00"),
            marks("397040.12", "401850.99"),
        )
        assertEquals(
            listOf("400000.00", "400500.00", "401000.00", "401500.00", "402000.00"),
            marks("400000.00", "402000.00"),
        )
        assertEquals(listOf("400000.00", "400100.00", "400200.00", "400300.00"), marks("400000.00", "400300.00"))
    }

    @Test
    fun `small spans use cents and steps of 25 appear from 10 up`() {
        assertEquals(
            listOf("400000.00", "400000.05", "400000.10", "400000.15", "400000.20", "400000.25"),
            marks("400000.01", "400000.25"),
        )
        assertEquals(
            listOf("400000.00", "400025.00", "400050.00", "400075.00", "400100.00", "400125.00"),
            marks("400010", "400110"),
        )
    }

    @Test
    fun `a flat price is centered on 4 steps of about 1 in 800`() {
        assertEquals(
            listOf("399000.00", "399500.00", "400000.00", "400500.00", "401000.00"),
            marks("400000.00", "400000.00"),
        )
    }

    @Test
    fun `no axis when its bottom would not be a price`() {
        assertNull(marks("0.50", "5.00"))
        assertNull(marks("0.01", "0.01"))
        assertNotNull(marks("1.00", "5.00"))
    }

    @Test
    fun `formatAxis leaves out zero cents only`() {
        assertEquals("R\$\u00A0400.500", quote("400500.00").formatAxis())
        assertEquals("R\$\u00A0400.000,10", quote("400000.10").formatAxis())
        assertEquals("400.000,12345", quote("400000.12345").formatNumber())
    }

    @Test
    fun `the 24 h change is truncated toward zero to 2 decimals`() {
        assertEquals("+0,52%", VariationPercent.between(quote("398000"), quote("400100")).format())
        assertEquals(Trend.Up, VariationPercent.between(quote("398000"), quote("400100")).trend)
        assertEquals("−0,52%", VariationPercent.between(quote("400100"), quote("398000")).format())
        assertEquals("0,00%", VariationPercent.between(quote("400000"), quote("400000")).format())
        assertEquals(Trend.Flat, VariationPercent.between(quote("400000"), quote("400010")).trend)
    }

    @Test
    fun `the spread keeps every decimal and its sign`() {
        assertEquals("R\$\u00A01.000,00", PriceDifference.between(quote("400000"), quote("401000")).format())
        assertEquals("−R\$\u00A00,12345", PriceDifference.between(quote("400000.12345"), quote("400000")).format())
        assertEquals("R\$\u00A00,00", PriceDifference.between(quote("400000"), quote("400000")).format())
    }
}

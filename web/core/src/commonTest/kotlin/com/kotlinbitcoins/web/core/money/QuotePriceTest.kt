package com.kotlinbitcoins.web.core.money

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.support.failureError
import com.kotlinbitcoins.web.core.support.successValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuotePriceTest {
    private fun quote(raw: String): QuotePrice = QuotePrice.parseApi(raw).successValue()

    @Test
    fun `parseApi accepts zero to eight decimals and requires a positive value`() {
        listOf("400000", "400000.5", "400000.12345", "400000.12345678", "0.00000001").forEach { raw ->
            quote(raw)
        }
        listOf("0", "0.00", "-1", "1e5", "400000.", "400000.123456789", "", "400.000,00").forEach { raw ->
            assertEquals(ApiError.Unexpected, QuotePrice.parseApi(raw).failureError(), "\"$raw\" must be refused")
        }
    }

    @Test
    fun `format keeps every received decimal with a minimum of two`() {
        assertEquals("R$ 400.000,00", quote("400000").format())
        assertEquals("R$ 400.000,50", quote("400000.5").format())
        assertEquals("R$ 400.000,12345", quote("400000.12345").format())
        assertEquals("R$ 400.000,12345678", quote("400000.12345678").format())
        assertEquals("R$ 0,00000001", quote("0.00000001").format())
    }

    @Test
    fun `compare by value`() {
        assertTrue(quote("400000.5") > quote("400000.49999999"))
        assertEquals(0, quote("400000").compareTo(quote("400000.00")))
    }
}

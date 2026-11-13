package com.kotlinbitcoins.web.core.money

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.format.AmountInputParser
import com.kotlinbitcoins.web.core.support.failureError
import com.kotlinbitcoins.web.core.support.successValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoneyTest {
    private fun money(raw: String): Money = Money.parseApi(raw).successValue()

    @Test
    fun `parseApi accepts only the contract pattern`() {
        listOf("0.01", "100.50", "99999999999.99", "0.00").forEach { raw ->
            assertEquals(raw, money(raw).toApiString())
        }
        listOf("1e3", "-1.00", "1.0", "1", "1.000", "", " 1.00", "1,00", "R$ 1.00", "1.00 ").forEach { raw ->
            assertEquals(ApiError.Unexpected, Money.parseApi(raw).failureError(), "\"$raw\" must be refused")
        }
    }

    @Test
    fun `format uses pt-BR grouping and a non-breaking space after the symbol`() {
        assertEquals("R$ 99.999.999.999,99", money("99999999999.99").format())
        assertEquals("R$ 0,01", money("0.01").format())
        assertEquals("R$ 1.234,56", money("1234.56").format())
        assertEquals("R$ 100,00", money("100.00").format())
        assertEquals("R$ 0,00", Money.ZERO.format())
        assertEquals(' ', money("1.00").format()[2])
    }

    @Test
    fun `user input becomes the API string`() {
        assertEquals("1234.56", AmountInputParser.parse("1234,56").successValue().toApiString())
        assertEquals("1234.00", AmountInputParser.parse("1.234").successValue().toApiString())
    }

    @Test
    fun `plus is exact`() {
        assertEquals(money("0.30"), money("0.10") + money("0.20"))
        assertEquals(money("99999999999.99"), money("99999999999.98") + money("0.01"))
        assertEquals("100000000000.00", (money("99999999999.99") + money("0.01")).toApiString())
    }

    @Test
    fun `no scientific notation in any case`() {
        val values = listOf("0.01", "0.10", "10000000000.00", "99999999999.99", "100000000000000000.00", "0.00")
        values.forEach { raw ->
            val value = money(raw)
            assertFalse(value.toApiString().contains('E', ignoreCase = true), raw)
            assertFalse(value.format().contains('E', ignoreCase = true), raw)
            assertEquals(raw, value.toApiString())
        }
        assertEquals("10000000000.00", (money("5000000000.00") + money("5000000000.00")).toApiString())
    }

    @Test
    fun `compare and zero`() {
        assertTrue(money("0.01") > Money.ZERO)
        assertTrue(money("10.00") < money("10.01"))
        assertEquals(0, money("10.00").compareTo(money("10.00")))
        assertTrue(money("0.00").isZero())
        assertFalse(money("0.01").isZero())
    }

    @Test
    fun `toString does not need a locale`() {
        assertEquals(money("1.50"), Money.parseApi("1.50").successValue())
    }
}

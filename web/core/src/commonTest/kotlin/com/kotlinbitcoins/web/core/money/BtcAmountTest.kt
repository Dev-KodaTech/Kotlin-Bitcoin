package com.kotlinbitcoins.web.core.money

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.support.failureError
import com.kotlinbitcoins.web.core.support.successValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BtcAmountTest {
    private fun btc(raw: String): BtcAmount = BtcAmount.parseApi(raw).successValue()

    @Test
    fun `parseApi accepts only the contract pattern`() {
        listOf("0.00000001", "0.00100000", "21000000.00000000", "0.00000000").forEach { raw ->
            assertEquals(raw, btc(raw).toApiString())
        }
        listOf("0.1", "1e-8", "-0.00000001", "0.000000001", "1", "", "0,00000001").forEach { raw ->
            assertEquals(ApiError.Unexpected, BtcAmount.parseApi(raw).failureError(), "\"$raw\" must be refused")
        }
    }

    @Test
    fun `format always shows eight decimals`() {
        assertEquals("0,00000001 BTC", btc("0.00000001").format())
        assertEquals("21.000.000,00000000 BTC", btc("21000000.00000000").format())
        assertEquals("0,00100000 BTC", btc("0.00100000").format())
    }

    @Test
    fun `below one satoshi only for zero`() {
        assertTrue(btc("0.00000000").isBelowOneSatoshi())
        assertFalse(btc("0.00000001").isBelowOneSatoshi())
        assertFalse(btc("1.00000000").isBelowOneSatoshi())
    }

    @Test
    fun `plus is exact`() {
        assertEquals(btc("0.00000003"), btc("0.00000001") + btc("0.00000002"))
        assertEquals("21000000.00000000", (btc("20999999.99999999") + btc("0.00000001")).toApiString())
    }
}

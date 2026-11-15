package com.kotlinbitcoins.web.core.estimate

import com.kotlinbitcoins.web.core.model.Quote
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.support.successValue
import com.kotlinbitcoins.web.core.time.ApiInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuyEstimateTest {
    private fun money(raw: String): Money = Money.parseApi(raw).successValue()

    private fun quote(
        sell: String,
        buy: String = "1.00",
        at: String = "2026-11-09T08:10:00-03:00",
    ): Quote =
        Quote(
            buyPrice = QuotePrice.parseApi(buy).successValue(),
            sellPrice = QuotePrice.parseApi(sell).successValue(),
            quotedAt = ApiInstant.parseApi(at).successValue(),
        )

    @Test
    fun `the estimate is the amount divided by the sell price`() {
        val estimate = BuyEstimate.of(money("400.00"), quote(sell = "400000.00"))

        assertEquals("0.00100000", estimate.btc.toApiString())
        assertEquals("0,00100000 BTC", estimate.btc.format())
        assertFalse(estimate.belowOneSatoshi)
    }

    @Test
    fun `the eighth decimal is truncated, never rounded`() {
        assertEquals("0.00033333", BuyEstimate.of(money("100.00"), quote(sell = "300000.00")).btc.toApiString())
        // 200 ÷ 300000 = 0.000666666…: rounding would give 0.00066667.
        assertEquals("0.00066666", BuyEstimate.of(money("200.00"), quote(sell = "300000.00")).btc.toApiString())
    }

    @Test
    fun `an amount below one satoshi is zero BTC and flagged`() {
        // 0.01 ÷ 2000000 = 0.000000005: half a satoshi. At 400000.00, R$ 0,01 still buys 2 satoshis.
        val estimate = BuyEstimate.of(money("0.01"), quote(sell = "2000000.00"))

        assertEquals("0.00000000", estimate.btc.toApiString())
        assertTrue(estimate.belowOneSatoshi)
    }

    @Test
    fun `exactly one satoshi is not flagged`() {
        val estimate = BuyEstimate.of(money("0.01"), quote(sell = "1000000.00"))

        assertEquals("0.00000001", estimate.btc.toApiString())
        assertFalse(estimate.belowOneSatoshi)
    }

    @Test
    fun `the estimate carries the quote it used, with the sell price and its instant`() {
        val used = quote(sell = "400000.12345678", buy = "399000.00", at = "2026-11-09T08:10:07-03:00")

        val estimate = BuyEstimate.of(money("400.00"), used)

        assertEquals(used, estimate.quote)
        assertEquals("R$ 400.000,12345678", estimate.quote.sellPrice.format())
        assertEquals(ApiInstant.parseApi("2026-11-09T08:10:07-03:00").successValue(), estimate.quote.quotedAt)
        assertEquals("0.00099999", estimate.btc.toApiString())
    }
}

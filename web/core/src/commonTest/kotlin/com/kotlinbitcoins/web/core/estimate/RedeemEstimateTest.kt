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

class RedeemEstimateTest {
    private fun money(raw: String): Money = Money.parseApi(raw).successValue()

    private fun quote(
        buy: String,
        sell: String = "1.00",
        at: String = "2026-11-09T09:00:00-03:00",
    ): Quote =
        Quote(
            buyPrice = QuotePrice.parseApi(buy).successValue(),
            sellPrice = QuotePrice.parseApi(sell).successValue(),
            quotedAt = ApiInstant.parseApi(at).successValue(),
        )

    @Test
    fun `the estimate is the amount divided by the buy price, truncated at the eighth decimal`() {
        // 500 ÷ 440000 = 0.001136363…
        val estimate = RedeemEstimate.of(money("500.00"), quote(buy = "440000.00"), fromRedeemAll = false)

        assertEquals("0.00113636", estimate.btc.toApiString())
        assertEquals("0,00113636 BTC", estimate.btc.format())
        // 200 ÷ 300000 = 0.000666666…: rounding would give 0.00066667.
        assertEquals(
            "0.00066666",
            RedeemEstimate.of(money("200.00"), quote(buy = "300000.00"), fromRedeemAll = false).btc.toApiString(),
        )
    }

    @Test
    fun `the estimate uses the buy price, not the sell price`() {
        val estimate = RedeemEstimate.of(money("400.00"), quote(buy = "400000.00", sell = "800000.00"), false)

        assertEquals("0.00100000", estimate.btc.toApiString())
    }

    @Test
    fun `fromRedeemAll is propagated`() {
        assertTrue(RedeemEstimate.of(money("800.00"), quote(buy = "400000.00"), fromRedeemAll = true).fromRedeemAll)
        assertFalse(RedeemEstimate.of(money("800.00"), quote(buy = "400000.00"), fromRedeemAll = false).fromRedeemAll)
    }

    @Test
    fun `the estimate carries the quote it used, with the buy price and its instant`() {
        val used = quote(buy = "440000.12345678", at = "2026-11-09T09:00:07-03:00")

        val estimate = RedeemEstimate.of(money("500.00"), used, fromRedeemAll = false)

        assertEquals(used, estimate.quote)
        assertEquals("R$ 440.000,12345678", estimate.quote.buyPrice.format())
        assertEquals(ApiInstant.parseApi("2026-11-09T09:00:07-03:00").successValue(), estimate.quote.quotedAt)
    }
}

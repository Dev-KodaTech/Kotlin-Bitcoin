package com.kotlinbitcoins.web.core.estimate

import com.kotlinbitcoins.web.core.model.Quote
import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.DecimalSupport
import com.kotlinbitcoins.web.core.money.Money

/**
 * Labeled estimate of a purchase (FR-015, data-model §4.3): `amount ÷ sellPrice`, truncated (FLOOR) to 8 decimals.
 * It is only shown in the review with the label "Estimativa"; the result of a purchase comes from the API. It carries
 * the [quote] it used, so the review shows that price and its instant (US4-1). [belowOneSatoshi] only warns: the
 * confirmation stays enabled and the API decides (FR-016).
 */
data class BuyEstimate(
    val btc: BtcAmount,
    val quote: Quote,
    val belowOneSatoshi: Boolean,
) {
    companion object {
        fun of(
            amount: Money,
            quote: Quote,
        ): BuyEstimate {
            val plain = DecimalSupport.divideFloor(amount.toApiString(), quote.sellPrice.toPlainString(), BTC_SCALE)
            val btc = BtcAmount.ofValidated(plain)
            return BuyEstimate(btc = btc, quote = quote, belowOneSatoshi = btc.isBelowOneSatoshi())
        }

        private const val BTC_SCALE = 8
    }
}

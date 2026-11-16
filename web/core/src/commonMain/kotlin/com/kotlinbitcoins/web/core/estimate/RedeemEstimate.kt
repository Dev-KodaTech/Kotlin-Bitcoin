package com.kotlinbitcoins.web.core.estimate

import com.kotlinbitcoins.web.core.model.Quote
import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.DecimalSupport
import com.kotlinbitcoins.web.core.money.Money

/**
 * Labeled estimate of a redemption (FR-023, data-model §4.3): `amount ÷ buyPrice`, truncated (FLOOR) to 8 decimals.
 * It is an approximation that does **not** reproduce the FIFO liquidation of the API: the result of a redemption
 * comes from the API. It carries the [quote] it used (buy price and instant). [fromRedeemAll] is true when the amount
 * came from "Resgatar tudo", so the review warns that a falling quote may put it above the available (FR-022).
 */
data class RedeemEstimate(
    val btc: BtcAmount,
    val quote: Quote,
    val fromRedeemAll: Boolean,
) {
    companion object {
        fun of(
            amount: Money,
            quote: Quote,
            fromRedeemAll: Boolean,
        ): RedeemEstimate {
            val plain = DecimalSupport.divideFloor(amount.toApiString(), quote.buyPrice.toPlainString(), BTC_SCALE)
            return RedeemEstimate(btc = BtcAmount.ofValidated(plain), quote = quote, fromRedeemAll = fromRedeemAll)
        }

        private const val BTC_SCALE = 8
    }
}

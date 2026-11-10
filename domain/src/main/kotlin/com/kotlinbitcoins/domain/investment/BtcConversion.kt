package com.kotlinbitcoins.domain.investment

import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvalidAmount
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import com.kotlinbitcoins.domain.shared.QuotePrice
import java.math.BigDecimal
import java.math.RoundingMode

/** Pure conversions between reais and bitcoin. Every division states its rounding mode (R-04). */
object BtcConversion {
    private val SATS_PER_BTC = BigDecimal(BtcAmount.SATS_PER_BTC)
    private val MAX_SATS = BigDecimal(Long.MAX_VALUE)

    /**
     * Satoshis bought with [amount] at the platform's [sellPrice]: `FLOOR(amount × 10⁸ ÷ sellPrice)` (FR-013).
     * An amount worth less than one satoshi, or beyond the satoshi range, is an [InvalidAmount].
     */
    fun purchase(
        amount: PositiveMoney,
        sellPrice: QuotePrice,
    ): Outcome<InvalidAmount, BtcAmount> {
        val sats = amount.amount.multiply(SATS_PER_BTC).divide(sellPrice.value, 0, RoundingMode.FLOOR)
        return if (sats.signum() > 0 && sats <= MAX_SATS) {
            Outcome.Success(BtcAmount(sats.longValueExact()))
        } else {
            Outcome.Failure(InvalidAmount)
        }
    }
}

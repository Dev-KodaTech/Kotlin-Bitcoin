package com.kotlinbitcoins.domain.quote

import com.kotlinbitcoins.domain.shared.QuotePrice
import java.math.BigDecimal
import java.math.RoundingMode

/** Percentage variation between the quote of a purchase and the current quote (FR-017). */
object PriceVariation {
    private const val SCALE = 2
    private val ONE_HUNDRED = BigDecimal(100)

    /**
     * `(currentBuy − purchaseQuote) ÷ purchaseQuote × 100`, rounded `HALF_UP` to 2 decimal places. The division is
     * done once, at the end, so no precision is lost before rounding.
     */
    fun percent(
        purchaseQuote: QuotePrice,
        currentBuy: QuotePrice,
    ): BigDecimal =
        currentBuy.value
            .subtract(purchaseQuote.value)
            .multiply(ONE_HUNDRED)
            .divide(purchaseQuote.value, SCALE, RoundingMode.HALF_UP)
}

package com.kotlinbitcoins.domain.shared

import java.math.BigDecimal

/** Price received from the quote source that is not usable (zero, negative or more than 8 decimal places). */
data object InvalidQuotePrice

/** BTC price in reais, positive, kept exactly as received from the source (FR-011): never rounded. */
class QuotePrice private constructor(
    val value: BigDecimal,
) {
    override fun equals(other: Any?): Boolean = other is QuotePrice && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value.toPlainString()

    companion object {
        const val MAX_SCALE = 8

        fun of(value: BigDecimal): Outcome<InvalidQuotePrice, QuotePrice> =
            if (value.signum() > 0 && value.scale() <= MAX_SCALE) {
                Outcome.Success(QuotePrice(value))
            } else {
                Outcome.Failure(InvalidQuotePrice)
            }
    }
}

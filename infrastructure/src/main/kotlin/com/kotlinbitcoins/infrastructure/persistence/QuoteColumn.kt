package com.kotlinbitcoins.infrastructure.persistence

import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import java.math.BigDecimal

private const val MIN_QUOTE_SCALE = 2

/**
 * `NUMERIC(19,8)` pads every quote to 8 decimals. Trailing zeros are dropped again, keeping at least two
 * decimals, so a quote of `400000.00` reads back as `400000.00` and not `400000.00000000`. The value is the same.
 * The database only holds quotes that were valid when written, so a broken value is a programming error.
 */
internal fun quoteFromColumn(stored: BigDecimal): QuotePrice {
    val stripped = stored.stripTrailingZeros()
    val normalised = if (stripped.scale() < MIN_QUOTE_SCALE) stripped.setScale(MIN_QUOTE_SCALE) else stripped
    return (QuotePrice.of(normalised) as Outcome.Success).value
}

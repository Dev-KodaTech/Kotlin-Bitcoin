package com.kotlinbitcoins.domain.quote

import com.kotlinbitcoins.domain.shared.QuotePrice
import java.time.Duration
import java.time.Instant

/**
 * BTC price in reais at a given moment (FR-011, FR-012a).
 *
 * [buyPrice] is what the platform pays the customer (redemptions and position); [sellPrice] is what the platform
 * charges the customer (purchases). [quotedAt] is the instant the quote was obtained from the source, so a quote
 * reused from the cache keeps its original instant.
 */
data class Quote(
    val buyPrice: QuotePrice,
    val sellPrice: QuotePrice,
    val quotedAt: Instant,
) {
    /** Time elapsed between [quotedAt] and [now]. */
    fun ageAt(now: Instant): Duration = Duration.between(quotedAt, now)
}

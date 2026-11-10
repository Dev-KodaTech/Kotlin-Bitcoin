package com.kotlinbitcoins.domain.quote

import com.kotlinbitcoins.domain.shared.QuotePrice
import java.time.Duration
import java.time.Instant

/**
 * One point of the quote history (US10, FR-027). [slotAt] is an exact multiple of 10 minutes and identifies the
 * point: there is at most one snapshot per slot. [capturedAt] is the instant the source answered, which can be a
 * little after the slot started.
 */
data class QuoteSnapshot(
    val slotAt: Instant,
    val buyPrice: QuotePrice,
    val sellPrice: QuotePrice,
    val capturedAt: Instant,
) {
    init {
        require(slotFor(slotAt) == slotAt) { "slotAt must be a multiple of 10 minutes: $slotAt" }
    }

    companion object {
        /** Distance between two slots. */
        val SLOT_LENGTH: Duration = Duration.ofMinutes(10)

        /** The slot [instant] belongs to: the closest multiple of 10 minutes at or before it. */
        fun slotFor(instant: Instant): Instant {
            val slotSeconds = SLOT_LENGTH.seconds
            return Instant.ofEpochSecond(Math.floorDiv(instant.epochSecond, slotSeconds) * slotSeconds)
        }

        /** Builds the snapshot of [slotAt] from a [quote] obtained from the source. */
        fun from(
            quote: Quote,
            slotAt: Instant,
        ): QuoteSnapshot = QuoteSnapshot(slotAt, quote.buyPrice, quote.sellPrice, quote.quotedAt)
    }
}

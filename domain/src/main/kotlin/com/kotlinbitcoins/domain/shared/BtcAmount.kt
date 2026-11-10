package com.kotlinbitcoins.domain.shared

import java.math.BigDecimal

/** Bitcoin amount in satoshis, never negative (FR-032). Formatting with 8 places happens only at the edge. */
@JvmInline
value class BtcAmount(
    val sats: Long,
) {
    init {
        require(sats >= 0) { "BtcAmount cannot be negative" }
    }

    operator fun plus(other: BtcAmount): BtcAmount = BtcAmount(Math.addExact(sats, other.sats))

    operator fun minus(other: BtcAmount): BtcAmount = BtcAmount(Math.subtractExact(sats, other.sats))

    fun toBtcDecimal(): BigDecimal = BigDecimal.valueOf(sats, BTC_SCALE)

    companion object {
        const val SATS_PER_BTC = 100_000_000L
        const val BTC_SCALE = 8

        val ZERO = BtcAmount(0)
    }
}

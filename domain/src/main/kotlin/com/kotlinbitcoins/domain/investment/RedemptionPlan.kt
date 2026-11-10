package com.kotlinbitcoins.domain.investment

import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Money

/** The requested amount is above the current value of all active investments, or there are none (FR-022). */
data object RedemptionExceedsAvailable

/**
 * One investment liquidated by a redemption: [soldSats] sold for [credit]. When [isPartial] the investment is only
 * partly sold and the rest becomes the plan's [RedemptionPlan.residual].
 */
data class Liquidation(
    val investment: Investment,
    val soldSats: BtcAmount,
    val credit: Money,
    val isPartial: Boolean,
)

/** What is left of a partially liquidated [origin]: [sats] that must be reinvested (FR-020). */
data class Residual(
    val origin: Investment,
    val sats: BtcAmount,
)

/**
 * Outcome of planning a redemption: the investments liquidated in FIFO order and, at most, one [residual]. The
 * credits add up to exactly the amount requested (FR-019a).
 */
data class RedemptionPlan(
    val liquidations: List<Liquidation>,
    val residual: Residual?,
) {
    val totalSoldSats: BtcAmount = liquidations.fold(BtcAmount.ZERO) { sum, liquidation -> sum + liquidation.soldSats }
}

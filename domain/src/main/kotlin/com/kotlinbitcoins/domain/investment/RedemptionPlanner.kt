package com.kotlinbitcoins.domain.investment

import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import com.kotlinbitcoins.domain.shared.QuotePrice

/**
 * Decides which investments a redemption liquidates and how (research R-04, FR-018..FR-022). Pure: it reads and
 * returns values, and persists nothing.
 *
 * The investments come in FIFO order. Each one is liquidated whole while the request still covers its current value;
 * the first one that the remainder does not cover is liquidated partially (the satoshis that cover the remainder,
 * rounded up) and its residue is reinvested. Crediting exactly the amount requested is the rule that fixes the
 * rounding: a whole liquidation credits its value rounded down to the cent, a partial one credits the remainder.
 */
object RedemptionPlanner {
    fun plan(
        requested: PositiveMoney,
        activeInFifoOrder: List<Investment>,
        buyPrice: QuotePrice,
    ): Outcome<RedemptionExceedsAvailable, RedemptionPlan> {
        val values = activeInFifoOrder.map { BtcConversion.currentValue(it.btcAmount, buyPrice) }
        val total = values.fold(Money.ZERO) { sum, value -> sum + value }
        if (requested.money > total) return Outcome.Failure(RedemptionExceedsAvailable)

        val liquidations = mutableListOf<Liquidation>()
        var residual: Residual? = null
        var remaining = requested.money
        for ((investment, value) in activeInFifoOrder.zip(values)) {
            if (remaining == Money.ZERO) break
            if (remaining >= value) {
                liquidations += Liquidation(investment, investment.btcAmount, value, isPartial = false)
                remaining -= value
            } else {
                val soldSats = BtcConversion.satsToCover(remaining, buyPrice)
                if (soldSats >= investment.btcAmount) {
                    // Rounding up consumed the whole investment: nothing is left to reinvest (FR-019a-3).
                    liquidations += Liquidation(investment, investment.btcAmount, remaining, isPartial = false)
                } else {
                    liquidations += Liquidation(investment, soldSats, remaining, isPartial = true)
                    residual = Residual(investment, investment.btcAmount - soldSats)
                }
                remaining = Money.ZERO
            }
        }
        return Outcome.Success(RedemptionPlan(liquidations, residual))
    }

    private operator fun BtcAmount.compareTo(other: BtcAmount): Int = sats.compareTo(other.sats)
}

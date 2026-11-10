package com.kotlinbitcoins.domain.investment

import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvestmentId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.QuotePrice
import java.time.Instant

/**
 * Bitcoin held by an account, bought at [purchaseQuote] (data-model §2). The FIFO queue of redemptions orders
 * investments by [purchasedAt] and then [fifoSequence]. An investment is never changed after it is created, except
 * for the transition to [InvestmentStatus.CLOSED]. It has one property per field of data-model §2.
 */
@Suppress("LongParameterList")
data class Investment(
    val id: InvestmentId,
    val accountId: AccountId,
    val purchasedAt: Instant,
    val fifoSequence: Long,
    val investedAmount: Money,
    val purchaseQuote: QuotePrice,
    val btcAmount: BtcAmount,
    val status: InvestmentStatus,
    val origin: InvestmentOrigin,
    val originInvestmentId: InvestmentId?,
    val closedAt: Instant?,
) {
    init {
        require(btcAmount.sats > 0) { "An investment must hold at least one satoshi" }
        require((origin == InvestmentOrigin.REINVESTMENT) == (originInvestmentId != null)) {
            "Only a REINVESTMENT has an origin investment"
        }
        require((status == InvestmentStatus.CLOSED) == (closedAt != null)) {
            "closedAt is set exactly when the investment is CLOSED"
        }
    }

    /** Closes an active investment: it was fully liquidated, or partially and its residue was reinvested (FR-020). */
    fun close(closedAt: Instant): Investment {
        check(status == InvestmentStatus.ACTIVE) { "Only an active investment can be closed" }
        return copy(status = InvestmentStatus.CLOSED, closedAt = closedAt)
    }

    companion object {
        /** A new investment created by a purchase (FR-014): active, no origin investment. */
        fun purchase(
            accountId: AccountId,
            purchasedAt: Instant,
            fifoSequence: Long,
            investedAmount: Money,
            purchaseQuote: QuotePrice,
            btcAmount: BtcAmount,
        ): Investment =
            Investment(
                id = InvestmentId.random(),
                accountId = accountId,
                purchasedAt = purchasedAt,
                fifoSequence = fifoSequence,
                investedAmount = investedAmount,
                purchaseQuote = purchaseQuote,
                btcAmount = btcAmount,
                status = InvestmentStatus.ACTIVE,
                origin = InvestmentOrigin.PURCHASE,
                originInvestmentId = null,
                closedAt = null,
            )

        /**
         * Investment created from the [residualSats] left by a partial redemption of [origin] (FR-020, FR-021). It
         * keeps the purchase date, FIFO sequence and quote of [origin], so it stays at the same place in the queue
         * and keeps the same variation. Its invested amount is the residue valued at that quote, rounded down.
         */
        fun reinvestmentOf(
            origin: Investment,
            residualSats: BtcAmount,
        ): Investment =
            Investment(
                id = InvestmentId.random(),
                accountId = origin.accountId,
                purchasedAt = origin.purchasedAt,
                fifoSequence = origin.fifoSequence,
                investedAmount = BtcConversion.currentValue(residualSats, origin.purchaseQuote),
                purchaseQuote = origin.purchaseQuote,
                btcAmount = residualSats,
                status = InvestmentStatus.ACTIVE,
                origin = InvestmentOrigin.REINVESTMENT,
                originInvestmentId = origin.id,
                closedAt = null,
            )
    }
}

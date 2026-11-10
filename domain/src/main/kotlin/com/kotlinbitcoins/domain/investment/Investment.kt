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
    }
}

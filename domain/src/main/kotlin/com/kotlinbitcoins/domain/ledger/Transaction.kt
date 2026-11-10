package com.kotlinbitcoins.domain.ledger

import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvestmentId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.PositiveMoney
import com.kotlinbitcoins.domain.shared.QuotePrice
import com.kotlinbitcoins.domain.shared.RedemptionRequestId
import com.kotlinbitcoins.domain.shared.TransactionId
import java.time.Instant

/**
 * Immutable statement line (data-model §2). Only a deposit has no bitcoin data: [btcAmount] and [quote] are null
 * exactly when [type] is [TransactionType.DEPOSIT], which the table also enforces with a CHECK. The lines of one
 * redemption share a [requestId] (FR-024a), which only they carry.
 */
data class Transaction(
    val id: TransactionId,
    val accountId: AccountId,
    val type: TransactionType,
    val amount: Money,
    val btcAmount: BtcAmount?,
    val quote: QuotePrice?,
    val investmentId: InvestmentId?,
    val requestId: RedemptionRequestId?,
    val occurredAt: Instant,
) {
    init {
        require((type == TransactionType.DEPOSIT) == (btcAmount == null && quote == null)) {
            "Only a DEPOSIT may omit btcAmount and quote"
        }
        require((type == TransactionType.REDEMPTION || type == TransactionType.REINVESTMENT) == (requestId != null)) {
            "requestId is set exactly on REDEMPTION and REINVESTMENT lines"
        }
    }

    companion object {
        fun deposit(
            accountId: AccountId,
            amount: PositiveMoney,
            occurredAt: Instant,
        ): Transaction =
            Transaction(
                id = TransactionId.random(),
                accountId = accountId,
                type = TransactionType.DEPOSIT,
                amount = amount.money,
                btcAmount = null,
                quote = null,
                investmentId = null,
                requestId = null,
                occurredAt = occurredAt,
            )

        /** Line of a purchase: [amount] invested, [btcAmount] bought at [sellPrice], creating [investmentId]. */
        @Suppress("LongParameterList")
        fun purchase(
            accountId: AccountId,
            amount: PositiveMoney,
            btcAmount: BtcAmount,
            sellPrice: QuotePrice,
            investmentId: InvestmentId,
            occurredAt: Instant,
        ): Transaction =
            Transaction(
                id = TransactionId.random(),
                accountId = accountId,
                type = TransactionType.PURCHASE,
                amount = amount.money,
                btcAmount = btcAmount,
                quote = sellPrice,
                investmentId = investmentId,
                requestId = null,
                occurredAt = occurredAt,
            )

        /**
         * Line of the liquidation of [investmentId] in a redemption: [amount] credited for [soldSats] sold at the
         * platform's [buyPrice]. [amount] is zero when the satoshis are worth less than a cent (FR-017).
         */
        @Suppress("LongParameterList")
        fun redemption(
            accountId: AccountId,
            amount: Money,
            soldSats: BtcAmount,
            buyPrice: QuotePrice,
            investmentId: InvestmentId,
            requestId: RedemptionRequestId,
            occurredAt: Instant,
        ): Transaction =
            Transaction(
                id = TransactionId.random(),
                accountId = accountId,
                type = TransactionType.REDEMPTION,
                amount = amount,
                btcAmount = soldSats,
                quote = buyPrice,
                investmentId = investmentId,
                requestId = requestId,
                occurredAt = occurredAt,
            )

        /**
         * Line of the reinvestment of a partial redemption: [investedAmount] for the [residualSats] kept, at the
         * [originalQuote] of the investment they came from, as the new investment [investmentId].
         */
        @Suppress("LongParameterList")
        fun reinvestment(
            accountId: AccountId,
            investedAmount: Money,
            residualSats: BtcAmount,
            originalQuote: QuotePrice,
            investmentId: InvestmentId,
            requestId: RedemptionRequestId,
            occurredAt: Instant,
        ): Transaction =
            Transaction(
                id = TransactionId.random(),
                accountId = accountId,
                type = TransactionType.REINVESTMENT,
                amount = investedAmount,
                btcAmount = residualSats,
                quote = originalQuote,
                investmentId = investmentId,
                requestId = requestId,
                occurredAt = occurredAt,
            )
    }
}

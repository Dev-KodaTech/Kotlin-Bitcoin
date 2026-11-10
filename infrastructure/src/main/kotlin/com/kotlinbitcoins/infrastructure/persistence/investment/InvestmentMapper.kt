package com.kotlinbitcoins.infrastructure.persistence.investment

import com.kotlinbitcoins.domain.investment.Investment
import com.kotlinbitcoins.domain.investment.InvestmentOrigin
import com.kotlinbitcoins.domain.investment.InvestmentStatus
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvestmentId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.infrastructure.persistence.quoteFromColumn

/** Converts between [Investment] and its table row. Money keeps its scale; satoshis are plain integers. */
object InvestmentMapper {
    fun toEntity(investment: Investment): InvestmentJpaEntity =
        InvestmentJpaEntity(
            id = investment.id.value,
            accountId = investment.accountId.value,
            purchasedAt = investment.purchasedAt,
            fifoSequence = investment.fifoSequence,
            investedAmount = investment.investedAmount.amount,
            purchaseQuote = investment.purchaseQuote.value,
            btcAmountSats = investment.btcAmount.sats,
            status = investment.status.name,
            origin = investment.origin.name,
            originInvestmentId = investment.originInvestmentId?.value,
            closedAt = investment.closedAt,
        )

    /** Rebuilds an investment the database already holds, so a broken invariant is a programming error. */
    fun toDomain(entity: InvestmentJpaEntity): Investment =
        Investment(
            id = InvestmentId(entity.id),
            accountId = AccountId(entity.accountId),
            purchasedAt = entity.purchasedAt,
            fifoSequence = entity.fifoSequence,
            investedAmount = (Money.of(entity.investedAmount) as Outcome.Success).value,
            purchaseQuote = quoteFromColumn(entity.purchaseQuote),
            btcAmount = BtcAmount(entity.btcAmountSats),
            status = InvestmentStatus.valueOf(entity.status),
            origin = InvestmentOrigin.valueOf(entity.origin),
            originInvestmentId = entity.originInvestmentId?.let(::InvestmentId),
            closedAt = entity.closedAt,
        )
}

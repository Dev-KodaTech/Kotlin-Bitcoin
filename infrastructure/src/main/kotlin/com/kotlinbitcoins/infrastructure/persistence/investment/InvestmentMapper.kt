package com.kotlinbitcoins.infrastructure.persistence.investment

import com.kotlinbitcoins.domain.investment.Investment
import com.kotlinbitcoins.domain.investment.InvestmentOrigin
import com.kotlinbitcoins.domain.investment.InvestmentStatus
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvestmentId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import java.math.BigDecimal

/** Converts between [Investment] and its table row. Money keeps its scale; satoshis are plain integers. */
object InvestmentMapper {
    private const val MIN_QUOTE_SCALE = 2

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
            purchaseQuote = quote(entity.purchaseQuote),
            btcAmount = BtcAmount(entity.btcAmountSats),
            status = InvestmentStatus.valueOf(entity.status),
            origin = InvestmentOrigin.valueOf(entity.origin),
            originInvestmentId = entity.originInvestmentId?.let(::InvestmentId),
            closedAt = entity.closedAt,
        )

    /**
     * `NUMERIC(19,8)` pads every quote to 8 decimals. Trailing zeros are dropped again, keeping at least two
     * decimals, so a quote of `400000.00` reads back as `400000.00` and not `400000.00000000`. The value is the same.
     */
    private fun quote(stored: BigDecimal): QuotePrice {
        val stripped = stored.stripTrailingZeros()
        val normalised = if (stripped.scale() < MIN_QUOTE_SCALE) stripped.setScale(MIN_QUOTE_SCALE) else stripped
        return (QuotePrice.of(normalised) as Outcome.Success).value
    }
}

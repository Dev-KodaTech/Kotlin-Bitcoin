package com.kotlinbitcoins.infrastructure.persistence.ledger

import com.kotlinbitcoins.domain.ledger.Transaction
import com.kotlinbitcoins.domain.ledger.TransactionType
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvestmentId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.RedemptionRequestId
import com.kotlinbitcoins.domain.shared.TransactionId
import com.kotlinbitcoins.infrastructure.persistence.quoteFromColumn

/** Converts a domain [Transaction] into its table row. Money keeps its scale; satoshis are plain integers. */
object TransactionMapper {
    fun toEntity(transaction: Transaction): TransactionJpaEntity =
        TransactionJpaEntity(
            id = transaction.id.value,
            accountId = transaction.accountId.value,
            type = transaction.type.name,
            amount = transaction.amount.amount,
            btcAmountSats = transaction.btcAmount?.sats,
            quote = transaction.quote?.value,
            investmentId = transaction.investmentId?.value,
            requestId = transaction.requestId?.value,
            occurredAt = transaction.occurredAt,
        )

    /** Rebuilds a line the database already holds, so a broken invariant is a programming error. */
    fun toDomain(entity: TransactionJpaEntity): Transaction =
        Transaction(
            id = TransactionId(entity.id),
            accountId = AccountId(entity.accountId),
            type = TransactionType.valueOf(entity.type),
            amount = (Money.of(entity.amount) as Outcome.Success).value,
            btcAmount = entity.btcAmountSats?.let(::BtcAmount),
            quote = entity.quote?.let(::quoteFromColumn),
            investmentId = entity.investmentId?.let(::InvestmentId),
            requestId = entity.requestId?.let(::RedemptionRequestId),
            occurredAt = entity.occurredAt,
        )
}

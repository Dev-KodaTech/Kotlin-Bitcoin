package com.kotlinbitcoins.infrastructure.persistence.ledger

import com.kotlinbitcoins.domain.ledger.Transaction

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
}

package com.kotlinbitcoins.infrastructure.persistence.ledger

import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.domain.ledger.Transaction
import com.kotlinbitcoins.domain.ledger.TransactionType
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.infrastructure.persistence.persistenceCall
import java.time.Instant

/** [TransactionRepository] over Spring Data JPA. No exception leaves this class (R-03, R-05). */
class TransactionRepositoryAdapter(
    private val repository: SpringDataTransactionRepository,
) : TransactionRepository {
    override fun saveAll(transactions: List<Transaction>): Outcome<InfrastructureFailure, Unit> =
        persistenceCall("transaction.saveAll") {
            // Flush now so constraint violations surface here, not at commit time.
            repository.saveAllAndFlush(transactions.map(TransactionMapper::toEntity))
            Unit
        }

    override fun findByAccountBetween(
        accountId: AccountId,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): Outcome<InfrastructureFailure, List<Transaction>> =
        persistenceCall("transaction.findByAccountBetween") {
            repository.findBetween(accountId.value, fromInclusive, toExclusive).map(TransactionMapper::toDomain)
        }

    override fun sumBtcByTypeBetween(
        type: TransactionType,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): Outcome<InfrastructureFailure, BtcAmount> =
        persistenceCall("transaction.sumBtcByTypeBetween") {
            BtcAmount(repository.sumSatsBetween(type.name, fromInclusive, toExclusive))
        }
}

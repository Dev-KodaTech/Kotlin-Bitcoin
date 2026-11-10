package com.kotlinbitcoins.application.port

import com.kotlinbitcoins.domain.ledger.Transaction
import com.kotlinbitcoins.domain.ledger.TransactionType
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Outcome
import java.time.Instant

interface TransactionRepository {
    /** Persists statement lines. Must run inside the caller's transaction so lines and balance change together. */
    fun saveAll(transactions: List<Transaction>): Outcome<InfrastructureFailure, Unit>

    /** Lines of [accountId] with `fromInclusive <= occurredAt < toExclusive`, newest first (FR-025). */
    fun findByAccountBetween(
        accountId: AccountId,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): Outcome<InfrastructureFailure, List<Transaction>>

    /**
     * Sum of the bitcoin of the lines of [type] of every account with `fromInclusive <= occurredAt < toExclusive`,
     * aggregated by the database; zero when there are none (FR-026).
     */
    fun sumBtcByTypeBetween(
        type: TransactionType,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): Outcome<InfrastructureFailure, BtcAmount>
}

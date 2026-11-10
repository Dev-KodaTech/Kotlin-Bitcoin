package com.kotlinbitcoins.application.port

import com.kotlinbitcoins.domain.ledger.Transaction
import com.kotlinbitcoins.domain.shared.Outcome

interface TransactionRepository {
    /** Persists statement lines. Must run inside the caller's transaction so lines and balance change together. */
    fun saveAll(transactions: List<Transaction>): Outcome<InfrastructureFailure, Unit>
}

package com.kotlinbitcoins.application.port

import com.kotlinbitcoins.domain.shared.Outcome

/**
 * Runs [block] atomically (FR-034).
 *
 * Contract:
 * - [block] returns `Success` → commit;
 * - [block] returns `Failure` → rollback, without any exception;
 * - the commit itself fails → rollback and `Failure(onInfrastructureFailure(...))`, without any exception.
 */
interface TransactionRunner {
    fun <E, T> run(
        onInfrastructureFailure: (InfrastructureFailure) -> E,
        block: () -> Outcome<E, T>,
    ): Outcome<E, T>
}

package com.kotlinbitcoins.infrastructure.persistence

import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.domain.shared.Outcome
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * [TransactionRunner] over Spring's [TransactionTemplate]. A `Failure` from the block marks the transaction
 * rollback-only (no exception involved); a failure to begin or commit becomes `Failure(onInfrastructureFailure(...))`.
 */
class JpaTransactionRunner(
    transactionManager: PlatformTransactionManager,
) : TransactionRunner {
    private val template = TransactionTemplate(transactionManager)

    @Suppress("TooGenericExceptionCaught")
    override fun <E, T> run(
        onInfrastructureFailure: (InfrastructureFailure) -> E,
        block: () -> Outcome<E, T>,
    ): Outcome<E, T> =
        try {
            template.execute { status ->
                block().also { if (it is Outcome.Failure) status.setRollbackOnly() }
            } ?: error("TransactionTemplate returned null for a non-null block result")
        } catch (exception: RuntimeException) {
            if (!isPersistenceFailure(exception)) throw exception
            Outcome.Failure(onInfrastructureFailure(toInfrastructureFailure("transaction", exception)))
        }
}

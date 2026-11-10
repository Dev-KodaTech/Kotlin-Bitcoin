package com.kotlinbitcoins.infrastructure.persistence

import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.domain.shared.Outcome
import jakarta.persistence.PersistenceException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.transaction.TransactionException

private val logger = LoggerFactory.getLogger("com.kotlinbitcoins.infrastructure.persistence")

/** Returns true for the exception families a database adapter must convert into [InfrastructureFailure]. */
internal fun isPersistenceFailure(exception: Exception): Boolean =
    exception is DataAccessException || exception is TransactionException || exception is PersistenceException

/** Logs [exception] (class only in the value, full detail in the log) and converts it into a failure value. */
internal fun toInfrastructureFailure(
    operation: String,
    exception: Exception,
): InfrastructureFailure {
    logger.warn("Database operation '{}' failed", operation, exception)
    return InfrastructureFailure("$operation failed: ${exception.javaClass.simpleName}")
}

/** Runs a database [block] so that no exception crosses into the application layer (R-03, R-05). */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> persistenceCall(
    operation: String,
    block: () -> T,
): Outcome<InfrastructureFailure, T> =
    try {
        Outcome.Success(block())
    } catch (exception: RuntimeException) {
        if (!isPersistenceFailure(exception)) throw exception
        Outcome.Failure(toInfrastructureFailure(operation, exception))
    }

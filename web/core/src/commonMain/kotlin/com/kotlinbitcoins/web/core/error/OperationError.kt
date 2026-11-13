package com.kotlinbitcoins.web.core.error

/** Errors of the financial operations: deposit, purchase and redemption (data-model §3, W-05). */
sealed interface OperationError {
    /** Definitive answer of the API: nothing was carried out. */
    data class Rejected(
        val error: ApiError,
    ) : OperationError

    /** The transport failed after sending: the result is unknown and the operation is never resent automatically. */
    data object Uncertain : OperationError
}

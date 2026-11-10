package com.kotlinbitcoins.application.ledger

import com.kotlinbitcoins.application.port.InfrastructureUnavailable

sealed interface StatementError {
    /** `from` after `to`, only one of the two dates, or a date that is not a valid ISO calendar date (FR-025). */
    data object InvalidDateRange : StatementError

    /** The authenticated account no longer exists, so the token no longer stands for anyone. */
    data object AccountNotFound : StatementError

    data object ServiceUnavailable : StatementError, InfrastructureUnavailable
}

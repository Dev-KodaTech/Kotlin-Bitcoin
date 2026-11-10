package com.kotlinbitcoins.application.account

import com.kotlinbitcoins.application.port.InfrastructureUnavailable

sealed interface DepositError {
    /** The authenticated account no longer exists, so the token no longer stands for anyone. */
    data object AccountNotFound : DepositError

    data object ServiceUnavailable : DepositError, InfrastructureUnavailable
}

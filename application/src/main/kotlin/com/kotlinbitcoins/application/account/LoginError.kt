package com.kotlinbitcoins.application.account

import com.kotlinbitcoins.application.port.InfrastructureUnavailable

sealed interface LoginError {
    /** Same error for unknown e-mail and wrong password, so the answer never reveals which one it was (FR-005). */
    data object InvalidCredentials : LoginError

    data object ServiceUnavailable : LoginError, InfrastructureUnavailable
}

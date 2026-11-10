package com.kotlinbitcoins.application.investment

import com.kotlinbitcoins.application.port.InfrastructureUnavailable

sealed interface RedemptionError {
    /** The amount is above the current value of the active investments, or the account has none (FR-022). */
    data object RedemptionExceedsAvailable : RedemptionError

    /** No quote from the source and none of 60 s or less in the cache (FR-012). */
    data object QuoteUnavailable : RedemptionError

    /** The authenticated account no longer exists, so the token no longer stands for anyone. */
    data object AccountNotFound : RedemptionError

    data object ServiceUnavailable : RedemptionError, InfrastructureUnavailable
}

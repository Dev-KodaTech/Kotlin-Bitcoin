package com.kotlinbitcoins.application.investment

import com.kotlinbitcoins.application.port.InfrastructureUnavailable

sealed interface PurchaseError {
    /** The amount converts to less than one satoshi (or is out of range) at the current sell price. */
    data object InvalidAmount : PurchaseError

    data object InsufficientBalance : PurchaseError

    /** No quote from the source and none of 60 s or less in the cache (FR-012). */
    data object QuoteUnavailable : PurchaseError

    /** The authenticated account no longer exists, so the token no longer stands for anyone. */
    data object AccountNotFound : PurchaseError

    data object ServiceUnavailable : PurchaseError, InfrastructureUnavailable
}

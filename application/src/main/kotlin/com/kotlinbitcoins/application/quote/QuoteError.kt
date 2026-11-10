package com.kotlinbitcoins.application.quote

sealed interface QuoteError {
    /** No quote from the source and none in the cache with 60 s or less (FR-012). */
    data object QuoteUnavailable : QuoteError
}

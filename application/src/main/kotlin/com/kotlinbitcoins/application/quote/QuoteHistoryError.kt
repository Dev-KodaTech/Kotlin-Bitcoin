package com.kotlinbitcoins.application.quote

import com.kotlinbitcoins.application.port.InfrastructureUnavailable

sealed interface QuoteHistoryError {
    /** The quote source gave no usable quote, so the slot has no point (FR-028). */
    data object SourceUnavailable : QuoteHistoryError

    data object ServiceUnavailable : QuoteHistoryError, InfrastructureUnavailable
}

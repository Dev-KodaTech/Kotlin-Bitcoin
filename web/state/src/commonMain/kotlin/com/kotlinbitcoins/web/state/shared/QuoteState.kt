package com.kotlinbitcoins.web.state.shared

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.model.Quote

/** The quote has its own state: "unavailable" is not a generic error (FR-013). */
sealed interface QuoteState {
    data object Loading : QuoteState

    data class Available(
        val quote: Quote,
    ) : QuoteState

    /** Never carries the previous quote: old values must not be shown (US2-3). */
    data object Unavailable : QuoteState

    data class Failed(
        val error: ApiError,
    ) : QuoteState
}

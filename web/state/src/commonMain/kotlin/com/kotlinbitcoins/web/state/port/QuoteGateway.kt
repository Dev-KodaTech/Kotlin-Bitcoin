package com.kotlinbitcoins.web.state.port

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Quote

/** Current bitcoin quote (`GET /quotes/current`); 503 QUOTE_UNAVAILABLE is [ApiError.QuoteUnavailable]. */
interface QuoteGateway {
    suspend fun current(): Outcome<ApiError, Quote>
}

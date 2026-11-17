package com.kotlinbitcoins.web.state.port

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Quote
import com.kotlinbitcoins.web.core.model.QuoteHistory

/**
 * Bitcoin quote: the current one (`GET /quotes/current`; 503 QUOTE_UNAVAILABLE is [ApiError.QuoteUnavailable]) and the
 * history of the last 24 h (`GET /quotes/history`, points in time order).
 */
interface QuoteGateway {
    suspend fun current(): Outcome<ApiError, Quote>

    suspend fun history(): Outcome<ApiError, QuoteHistory>
}

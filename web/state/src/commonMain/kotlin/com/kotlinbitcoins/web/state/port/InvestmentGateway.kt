package com.kotlinbitcoins.web.state.port

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Position

/** Investment position (`GET /me/investments`), in the API order; no quote is [ApiError.QuoteUnavailable]. */
interface InvestmentGateway {
    suspend fun position(): Outcome<ApiError, Position>
}

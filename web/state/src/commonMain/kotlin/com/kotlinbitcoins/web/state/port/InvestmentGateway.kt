package com.kotlinbitcoins.web.state.port

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.core.model.PurchaseResult
import com.kotlinbitcoins.web.core.money.Money

/**
 * Investment position (`GET /me/investments`), in the API order, and purchases (`POST /me/purchases`). No quote is
 * [ApiError.QuoteUnavailable].
 */
interface InvestmentGateway {
    suspend fun position(): Outcome<ApiError, Position>

    /** A financial operation: a lost response is [OperationError.Uncertain] and is never resent (FR-041). */
    suspend fun purchase(amount: Money): Outcome<OperationError, PurchaseResult>
}

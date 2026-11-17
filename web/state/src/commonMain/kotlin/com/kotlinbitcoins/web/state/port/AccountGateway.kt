package com.kotlinbitcoins.web.state.port

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.DepositResult
import com.kotlinbitcoins.web.core.money.Money

/** Balance and deposits of the authenticated account (`GET /me/balance`, `POST /me/deposits`), implemented by :data. */
interface AccountGateway {
    suspend fun balance(): Outcome<ApiError, Money>

    /** A financial operation: a lost response is [OperationError.Uncertain] and is never resent (FR-041). */
    suspend fun deposit(amount: Money): Outcome<OperationError, DepositResult>
}

package com.kotlinbitcoins.web.state.port

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.money.Money

/** Balance of the authenticated account (`GET /me/balance`), implemented by :data. */
interface AccountGateway {
    suspend fun balance(): Outcome<ApiError, Money>
}

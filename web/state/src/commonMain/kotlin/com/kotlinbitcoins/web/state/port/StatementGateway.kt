package com.kotlinbitcoins.web.state.port

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Statement
import com.kotlinbitcoins.web.core.statement.StatementFilter

/**
 * The statement (`GET /me/statement`): [StatementFilter.Default] asks for the default period of the API; a range
 * refused by the API is [ApiError.InvalidDateRange].
 */
interface StatementGateway {
    suspend fun statement(filter: StatementFilter): Outcome<ApiError, Statement>
}

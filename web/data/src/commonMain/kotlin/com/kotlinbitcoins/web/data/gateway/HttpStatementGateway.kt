package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Statement
import com.kotlinbitcoins.web.core.statement.StatementFilter
import com.kotlinbitcoins.web.data.dto.StatementDto
import com.kotlinbitcoins.web.data.http.ApiCall
import com.kotlinbitcoins.web.data.mapper.StatementMapper
import com.kotlinbitcoins.web.state.port.StatementGateway
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/**
 * [StatementGateway] over `GET /me/statement`: no query for the default period, `from`/`to` (yyyy-MM-dd) for a
 * range.
 */
class HttpStatementGateway(
    private val apiCall: ApiCall,
) : StatementGateway {
    override suspend fun statement(filter: StatementFilter): Outcome<ApiError, Statement> =
        apiCall.read(StatementDto.serializer(), StatementMapper::toStatement) {
            method = HttpMethod.Get
            url("me/statement")
            if (filter is StatementFilter.Range) {
                parameter("from", filter.from.toIso())
                parameter("to", filter.to.toIso())
            }
        }
}

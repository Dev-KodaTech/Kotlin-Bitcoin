package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.data.dto.PositionDto
import com.kotlinbitcoins.web.data.http.ApiCall
import com.kotlinbitcoins.web.data.mapper.PositionMapper
import com.kotlinbitcoins.web.state.port.InvestmentGateway
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/** [InvestmentGateway] over `GET /me/investments`. */
class HttpInvestmentGateway(
    private val apiCall: ApiCall,
) : InvestmentGateway {
    override suspend fun position(): Outcome<ApiError, Position> =
        apiCall.read(PositionDto.serializer(), PositionMapper::toPosition) {
            method = HttpMethod.Get
            url("me/investments")
        }
}

package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.DailyVolume
import com.kotlinbitcoins.web.data.dto.DailyVolumeDto
import com.kotlinbitcoins.web.data.http.ApiCall
import com.kotlinbitcoins.web.data.mapper.MarketMapper
import com.kotlinbitcoins.web.state.port.MarketGateway
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/** [MarketGateway] over `GET /market/daily-volume`; the totals keep their 8 decimals. */
class HttpMarketGateway(
    private val apiCall: ApiCall,
) : MarketGateway {
    override suspend fun dailyVolume(): Outcome<ApiError, DailyVolume> =
        apiCall.read(DailyVolumeDto.serializer(), MarketMapper::toDailyVolume) {
            method = HttpMethod.Get
            url("market/daily-volume")
        }
}

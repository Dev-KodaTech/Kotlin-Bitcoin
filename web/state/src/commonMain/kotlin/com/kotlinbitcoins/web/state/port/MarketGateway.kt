package com.kotlinbitcoins.web.state.port

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.DailyVolume

/** Market volume of the current Brasília day, all clients together (`GET /market/daily-volume`, FR-031). */
interface MarketGateway {
    suspend fun dailyVolume(): Outcome<ApiError, DailyVolume>
}

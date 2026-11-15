package com.kotlinbitcoins.web.state.port

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Profile

/** Profile of the authenticated customer (`GET /me`), implemented by :data. */
interface ProfileGateway {
    suspend fun profile(): Outcome<ApiError, Profile>
}

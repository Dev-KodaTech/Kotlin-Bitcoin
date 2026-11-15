package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Profile
import com.kotlinbitcoins.web.data.dto.ProfileDto
import com.kotlinbitcoins.web.data.http.ApiCall
import com.kotlinbitcoins.web.state.port.ProfileGateway
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/** [ProfileGateway] over `GET /me`. */
class HttpProfileGateway(
    private val apiCall: ApiCall,
) : ProfileGateway {
    override suspend fun profile(): Outcome<ApiError, Profile> =
        apiCall.read(ProfileDto.serializer(), { Outcome.Success(Profile(it.name, it.email)) }) {
            method = HttpMethod.Get
            url("me")
        }
}

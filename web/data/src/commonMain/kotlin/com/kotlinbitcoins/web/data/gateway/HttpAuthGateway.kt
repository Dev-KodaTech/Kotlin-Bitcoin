package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.AccountCreated
import com.kotlinbitcoins.web.core.model.LoginResult
import com.kotlinbitcoins.web.data.dto.AccountResponseDto
import com.kotlinbitcoins.web.data.dto.LoginRequestDto
import com.kotlinbitcoins.web.data.dto.LoginResponseDto
import com.kotlinbitcoins.web.data.dto.RegisterAccountRequestDto
import com.kotlinbitcoins.web.data.http.ApiCall
import com.kotlinbitcoins.web.state.port.AuthGateway
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlin.time.Duration.Companion.seconds

/** [AuthGateway] over the API. Both calls are public (no Authorization header, see AuthHeaderPlugin). */
class HttpAuthGateway(
    private val apiCall: ApiCall,
) : AuthGateway {
    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): Outcome<ApiError, AccountCreated> =
        apiCall.read(AccountResponseDto.serializer(), ::toAccountCreated) {
            method = HttpMethod.Post
            url("accounts")
            contentType(ContentType.Application.Json)
            setBody(RegisterAccountRequestDto(name, email, password))
        }

    override suspend fun login(
        email: String,
        password: String,
    ): Outcome<ApiError, LoginResult> =
        apiCall.read(LoginResponseDto.serializer(), ::toLoginResult) {
            method = HttpMethod.Post
            url("auth/login")
            contentType(ContentType.Application.Json)
            setBody(LoginRequestDto(email, password))
        }

    private fun toAccountCreated(dto: AccountResponseDto): Outcome<ApiError, AccountCreated> =
        Outcome.Success(AccountCreated(dto.id, dto.name, dto.email))

    /** A token without a positive lifetime is outside the contract: the session could not be timed. */
    private fun toLoginResult(dto: LoginResponseDto): Outcome<ApiError, LoginResult> =
        if (dto.accessToken.isNotEmpty() && dto.expiresIn > 0) {
            Outcome.Success(LoginResult(dto.accessToken, dto.expiresIn.seconds))
        } else {
            Outcome.Failure(ApiError.Unexpected)
        }
}

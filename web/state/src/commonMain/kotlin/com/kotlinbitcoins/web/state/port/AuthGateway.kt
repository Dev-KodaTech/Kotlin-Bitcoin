package com.kotlinbitcoins.web.state.port

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.AccountCreated
import com.kotlinbitcoins.web.core.model.LoginResult

/** Registration and login (`POST /accounts`, `POST /auth/login`), implemented by :data. */
interface AuthGateway {
    suspend fun register(
        name: String,
        email: String,
        password: String,
    ): Outcome<ApiError, AccountCreated>

    suspend fun login(
        email: String,
        password: String,
    ): Outcome<ApiError, LoginResult>
}

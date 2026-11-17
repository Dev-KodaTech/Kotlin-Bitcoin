package com.kotlinbitcoins.web.state.support

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.AccountCreated
import com.kotlinbitcoins.web.core.model.LoginResult
import com.kotlinbitcoins.web.state.port.AuthGateway
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration.Companion.seconds

/**
 * Records every call. Answers with [registerResult]/[loginResult]; while [gate] is not complete the calls stay
 * suspended, like a slow network (to test single flight).
 */
class FakeAuthGateway : AuthGateway {
    data class RegisterCall(
        val name: String,
        val email: String,
        val password: String,
    )

    data class LoginCall(
        val email: String,
        val password: String,
    )

    val registerCalls = mutableListOf<RegisterCall>()
    val loginCalls = mutableListOf<LoginCall>()

    var registerResult: Outcome<ApiError, AccountCreated> =
        Outcome.Success(AccountCreated("3f1c2a9e-7b4d-4e8a-9c21-5d6e7f8a9b0c", "Ana Souza", "ana@example.com"))
    var loginResult: Outcome<ApiError, LoginResult> = Outcome.Success(LoginResult(TOKEN, 3600.seconds))
    var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): Outcome<ApiError, AccountCreated> {
        registerCalls += RegisterCall(name, email, password)
        gate.await()
        return registerResult
    }

    override suspend fun login(
        email: String,
        password: String,
    ): Outcome<ApiError, LoginResult> {
        loginCalls += LoginCall(email, password)
        gate.await()
        return loginResult
    }

    companion object {
        const val TOKEN = "token-ana"
    }
}

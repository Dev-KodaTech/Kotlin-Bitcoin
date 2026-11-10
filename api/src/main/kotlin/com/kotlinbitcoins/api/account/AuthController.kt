package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.application.account.AuthenticateAccount
import com.kotlinbitcoins.application.account.LoginCommand
import com.kotlinbitcoins.domain.shared.fold
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Login (US1). Public route; answers the same error for unknown e-mail and wrong password (FR-005). */
@RestController
class AuthController(
    private val authenticateAccount: AuthenticateAccount,
    private val problems: ProblemDetailsMapper,
) {
    @PostMapping("/api/v1/auth/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ResponseEntity<*> =
        authenticateAccount
            .execute(LoginCommand(request.email.orEmpty(), request.password.orEmpty()))
            .fold(
                onFailure = problems::loginError,
                onSuccess = { ResponseEntity.ok(LoginResponse.of(it)) },
            )
}

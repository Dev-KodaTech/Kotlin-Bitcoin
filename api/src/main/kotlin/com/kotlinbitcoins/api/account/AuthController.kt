package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.openapi.PROBLEM_JSON
import com.kotlinbitcoins.api.openapi.ProblemBody
import com.kotlinbitcoins.application.account.AuthenticateAccount
import com.kotlinbitcoins.application.account.LoginCommand
import com.kotlinbitcoins.domain.shared.fold
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirements
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
    @SecurityRequirements
    @ApiResponse(
        responseCode = "200",
        description = "Token emitido",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = LoginResponse::class))],
    )
    @ApiResponse(
        responseCode = "400",
        description = "Entrada com formato inválido",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemBody::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "E-mail inexistente ou senha incorreta (mesma resposta)",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemBody::class))],
    )
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

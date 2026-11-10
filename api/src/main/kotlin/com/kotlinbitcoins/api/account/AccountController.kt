package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.api.openapi.PROBLEM_JSON
import com.kotlinbitcoins.api.openapi.ProblemBody
import com.kotlinbitcoins.application.account.RegisterAccount
import com.kotlinbitcoins.application.account.RegisterAccountCommand
import com.kotlinbitcoins.domain.shared.fold
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Account registration (US1). Public route. */
@RestController
class AccountController(
    private val registerAccount: RegisterAccount,
    private val formats: ApiFormats,
    private val problems: ProblemDetailsMapper,
) {
    @SecurityRequirements
    @ApiResponse(
        responseCode = "201",
        description = "Conta criada com saldo 0.00",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = AccountResponse::class))],
    )
    @ApiResponse(
        responseCode = "400",
        description = "Entrada com formato inválido",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemBody::class))],
    )
    @ApiResponse(
        responseCode = "409",
        description = "E-mail já cadastrado",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemBody::class))],
    )
    @PostMapping("/api/v1/accounts")
    fun register(
        @Valid @RequestBody request: RegisterAccountRequest,
    ): ResponseEntity<*> =
        registerAccount
            .execute(request.toCommand())
            .fold(
                onFailure = problems::registerAccountError,
                onSuccess = { ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.of(it, formats)) },
            )
}

private fun RegisterAccountRequest.toCommand() =
    RegisterAccountCommand(name.orEmpty(), email.orEmpty(), password.orEmpty())

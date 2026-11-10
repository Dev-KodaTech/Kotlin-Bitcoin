package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.application.account.RegisterAccount
import com.kotlinbitcoins.application.account.RegisterAccountCommand
import com.kotlinbitcoins.domain.shared.fold
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

package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.api.format.MoneyInputParser
import com.kotlinbitcoins.api.openapi.PROBLEM_JSON
import com.kotlinbitcoins.api.openapi.ProblemBody
import com.kotlinbitcoins.api.web.CurrentAccount
import com.kotlinbitcoins.application.account.DepositCommand
import com.kotlinbitcoins.application.account.DepositFunds
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.fold
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Deposit in reais into the authenticated account (US2, FR-008). The account comes from the token (FR-033). */
@RestController
class DepositController(
    private val depositFunds: DepositFunds,
    private val formats: ApiFormats,
    private val problems: ProblemDetailsMapper,
) {
    @ApiResponse(
        responseCode = "201",
        description = "Depósito creditado",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = DepositResponse::class))],
    )
    @ApiResponse(
        responseCode = "400",
        description = "Valor inválido",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemBody::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente, malformado, adulterado ou expirado",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemBody::class))],
    )
    @PostMapping("/api/v1/me/deposits")
    fun deposit(
        @CurrentAccount accountId: AccountId,
        @RequestBody request: AmountRequest,
    ): ResponseEntity<*> =
        MoneyInputParser.parse(request.amount).fold(
            onFailure = { problems.invalidAmount() },
            onSuccess = { amount ->
                depositFunds
                    .execute(DepositCommand(accountId, amount))
                    .fold(
                        onFailure = problems::depositError,
                        onSuccess = { ResponseEntity.status(HttpStatus.CREATED).body(DepositResponse.of(it, formats)) },
                    )
            },
        )
}

package com.kotlinbitcoins.api.investment

import com.kotlinbitcoins.api.account.AmountRequest
import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.api.format.MoneyInputParser
import com.kotlinbitcoins.api.openapi.PROBLEM_JSON
import com.kotlinbitcoins.api.openapi.ProblemBody
import com.kotlinbitcoins.api.web.CurrentAccount
import com.kotlinbitcoins.application.investment.RedeemCommand
import com.kotlinbitcoins.application.investment.RedeemInvestments
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

/** Redemption of an amount in reais from the authenticated account's investments (US7, FR-018). */
@RestController
class RedemptionController(
    private val redeemInvestments: RedeemInvestments,
    private val formats: ApiFormats,
    private val problems: ProblemDetailsMapper,
) {
    @ApiResponse(
        responseCode = "201",
        description = "Resgate efetuado",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = RedemptionResponse::class),
            ),
        ],
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
    @ApiResponse(
        responseCode = "422",
        description = "Resgate acima do valor atual total dos investimentos",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemBody::class))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "Sem cotação válida com até 60 s",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemBody::class))],
    )
    @PostMapping("/api/v1/me/redemptions")
    fun redeem(
        @CurrentAccount accountId: AccountId,
        @RequestBody request: AmountRequest,
    ): ResponseEntity<*> =
        MoneyInputParser.parse(request.amount).fold(
            onFailure = { problems.invalidAmount() },
            onSuccess = { amount ->
                redeemInvestments
                    .execute(RedeemCommand(accountId, amount))
                    .fold(
                        onFailure = problems::redemptionError,
                        onSuccess = {
                            ResponseEntity.status(HttpStatus.CREATED).body(RedemptionResponse.of(it, formats))
                        },
                    )
            },
        )
}

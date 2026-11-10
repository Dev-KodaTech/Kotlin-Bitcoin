package com.kotlinbitcoins.api.investment

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.api.openapi.PROBLEM_JSON
import com.kotlinbitcoins.api.openapi.ProblemBody
import com.kotlinbitcoins.api.web.CurrentAccount
import com.kotlinbitcoins.application.investment.GetInvestmentPosition
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.fold
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Active investments of the authenticated account with variation and current value (US6, FR-016, FR-017). */
@RestController
class PositionController(
    private val getInvestmentPosition: GetInvestmentPosition,
    private val formats: ApiFormats,
    private val problems: ProblemDetailsMapper,
) {
    @ApiResponse(
        responseCode = "200",
        description = "Investimentos ativos em ordem FIFO",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = PositionResponse::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente, malformado, adulterado ou expirado",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemBody::class))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "Sem cotação válida com até 60 s",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemBody::class))],
    )
    @GetMapping("/api/v1/me/investments")
    fun position(
        @CurrentAccount accountId: AccountId,
    ): ResponseEntity<*> =
        getInvestmentPosition
            .execute(accountId)
            .fold(
                onFailure = problems::getInvestmentPositionError,
                onSuccess = { ResponseEntity.ok(PositionResponse.of(it, formats)) },
            )
}

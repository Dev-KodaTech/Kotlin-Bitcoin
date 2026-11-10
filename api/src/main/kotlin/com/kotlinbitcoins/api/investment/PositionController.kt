package com.kotlinbitcoins.api.investment

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.api.web.CurrentAccount
import com.kotlinbitcoins.application.investment.GetInvestmentPosition
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.fold
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

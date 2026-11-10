package com.kotlinbitcoins.api.investment

import com.kotlinbitcoins.api.account.AmountRequest
import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.api.format.MoneyInputParser
import com.kotlinbitcoins.api.web.CurrentAccount
import com.kotlinbitcoins.application.investment.RedeemCommand
import com.kotlinbitcoins.application.investment.RedeemInvestments
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.fold
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

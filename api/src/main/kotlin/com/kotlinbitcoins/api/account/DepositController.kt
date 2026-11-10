package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.api.format.MoneyInputParser
import com.kotlinbitcoins.api.web.CurrentAccount
import com.kotlinbitcoins.application.account.DepositCommand
import com.kotlinbitcoins.application.account.DepositFunds
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.fold
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

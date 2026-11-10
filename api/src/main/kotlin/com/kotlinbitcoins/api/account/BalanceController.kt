package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.api.web.CurrentAccount
import com.kotlinbitcoins.application.account.GetBalance
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.fold
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class BalanceResponse(
    val balance: String,
) {
    companion object {
        fun of(
            balance: Money,
            formats: ApiFormats,
        ) = BalanceResponse(formats.money(balance))
    }
}

/** Balance in reais of the authenticated account (US3, FR-009). The account comes from the token (FR-033). */
@RestController
class BalanceController(
    private val getBalance: GetBalance,
    private val formats: ApiFormats,
    private val problems: ProblemDetailsMapper,
) {
    @GetMapping("/api/v1/me/balance")
    fun balance(
        @CurrentAccount accountId: AccountId,
    ): ResponseEntity<*> =
        getBalance
            .execute(accountId)
            .fold(
                onFailure = problems::getBalanceError,
                onSuccess = { ResponseEntity.ok(BalanceResponse.of(it, formats)) },
            )
}

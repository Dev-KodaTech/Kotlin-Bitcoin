package com.kotlinbitcoins.api.investment

import com.kotlinbitcoins.api.account.AmountRequest
import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.api.format.MoneyInputParser
import com.kotlinbitcoins.api.web.CurrentAccount
import com.kotlinbitcoins.application.investment.PurchaseBitcoin
import com.kotlinbitcoins.application.investment.PurchaseCommand
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.fold
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Purchase of bitcoin with reais from the authenticated account (US5, FR-013). The account comes from the token. */
@RestController
class PurchaseController(
    private val purchaseBitcoin: PurchaseBitcoin,
    private val formats: ApiFormats,
    private val problems: ProblemDetailsMapper,
) {
    @PostMapping("/api/v1/me/purchases")
    fun purchase(
        @CurrentAccount accountId: AccountId,
        @RequestBody request: AmountRequest,
    ): ResponseEntity<*> =
        MoneyInputParser.parse(request.amount).fold(
            onFailure = { problems.invalidAmount() },
            onSuccess = { amount ->
                purchaseBitcoin
                    .execute(PurchaseCommand(accountId, amount))
                    .fold(
                        onFailure = problems::purchaseError,
                        onSuccess = {
                            ResponseEntity
                                .status(
                                    HttpStatus.CREATED,
                                ).body(PurchaseResponse.of(it, formats))
                        },
                    )
            },
        )
}

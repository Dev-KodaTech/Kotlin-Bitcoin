package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.application.account.DepositReceipt

/**
 * Body of a request that carries one money amount. The amount is a JSON string, parsed strictly by
 * `MoneyInputParser` (R-10), so Bean Validation is not used here.
 */
data class AmountRequest(
    val amount: String?,
)

data class DepositResponse(
    val transactionId: String,
    val amount: String,
    val balance: String,
    val occurredAt: String,
) {
    companion object {
        fun of(
            receipt: DepositReceipt,
            formats: ApiFormats,
        ) = DepositResponse(
            transactionId = receipt.transactionId.toString(),
            amount = formats.money(receipt.amount),
            balance = formats.money(receipt.balance),
            occurredAt = formats.instant(receipt.occurredAt),
        )
    }
}

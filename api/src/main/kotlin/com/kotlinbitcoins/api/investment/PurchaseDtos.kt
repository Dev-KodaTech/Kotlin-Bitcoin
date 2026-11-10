@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.kotlinbitcoins.api.investment

import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.application.investment.PurchaseReceipt

data class PurchaseResponse(
    val investmentId: String,
    val transactionId: String,
    val amount: String,
    val btcAmount: String,
    val quote: String,
    val purchasedAt: String,
    val balance: String,
) {
    companion object {
        fun of(
            receipt: PurchaseReceipt,
            formats: ApiFormats,
        ) = PurchaseResponse(
            investmentId = receipt.investmentId.toString(),
            transactionId = receipt.transactionId.toString(),
            amount = formats.money(receipt.amount),
            btcAmount = formats.btc(receipt.btcAmount),
            quote = formats.quote(receipt.quote),
            purchasedAt = formats.instant(receipt.purchasedAt),
            balance = formats.money(receipt.balance),
        )
    }
}

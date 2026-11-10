@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.kotlinbitcoins.api.investment

import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.application.investment.LiquidationReceipt
import com.kotlinbitcoins.application.investment.RedemptionReceipt
import com.kotlinbitcoins.application.investment.ReinvestmentReceipt

data class LiquidationDto(
    val investmentId: String,
    val amount: String,
    val btcAmount: String,
    val isPartial: Boolean,
) {
    companion object {
        fun of(
            liquidation: LiquidationReceipt,
            formats: ApiFormats,
        ) = LiquidationDto(
            investmentId = liquidation.investmentId.toString(),
            amount = formats.money(liquidation.amount),
            btcAmount = formats.btc(liquidation.btcAmount),
            isPartial = liquidation.isPartial,
        )
    }
}

data class ReinvestmentDto(
    val investmentId: String,
    val btcAmount: String,
    val investedAmount: String,
    val purchaseQuote: String,
    val purchasedAt: String,
) {
    companion object {
        fun of(
            reinvestment: ReinvestmentReceipt,
            formats: ApiFormats,
        ) = ReinvestmentDto(
            investmentId = reinvestment.investmentId.toString(),
            btcAmount = formats.btc(reinvestment.btcAmount),
            investedAmount = formats.money(reinvestment.investedAmount),
            purchaseQuote = formats.quote(reinvestment.purchaseQuote),
            purchasedAt = formats.instant(reinvestment.purchasedAt),
        )
    }
}

/** `reinvestment` is always present in the JSON, `null` when the redemption left no residue. */
data class RedemptionResponse(
    val requestId: String,
    val amount: String,
    val btcSold: String,
    val quote: String,
    val balance: String,
    val occurredAt: String,
    val liquidations: List<LiquidationDto>,
    val reinvestment: ReinvestmentDto?,
) {
    companion object {
        fun of(
            receipt: RedemptionReceipt,
            formats: ApiFormats,
        ) = RedemptionResponse(
            requestId = receipt.requestId.toString(),
            amount = formats.money(receipt.amount),
            btcSold = formats.btc(receipt.btcSold),
            quote = formats.quote(receipt.quote),
            balance = formats.money(receipt.balance),
            occurredAt = formats.instant(receipt.occurredAt),
            liquidations = receipt.liquidations.map { LiquidationDto.of(it, formats) },
            reinvestment = receipt.reinvestment?.let { ReinvestmentDto.of(it, formats) },
        )
    }
}

@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.kotlinbitcoins.api.investment

import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.api.quote.QuoteResponse
import com.kotlinbitcoins.application.investment.InvestmentPosition
import com.kotlinbitcoins.application.investment.PositionItem

data class InvestmentPositionDto(
    val id: String,
    val purchasedAt: String,
    val investedAmount: String,
    val purchaseQuote: String,
    val btcAmount: String,
    val variationPercent: String,
    val currentValue: String,
) {
    companion object {
        fun of(
            item: PositionItem,
            formats: ApiFormats,
        ) = InvestmentPositionDto(
            id = item.investmentId.toString(),
            purchasedAt = formats.instant(item.purchasedAt),
            investedAmount = formats.money(item.investedAmount),
            purchaseQuote = formats.quote(item.purchaseQuote),
            btcAmount = formats.btc(item.btcAmount),
            variationPercent = formats.percent(item.variationPercent),
            currentValue = formats.money(item.currentValue),
        )
    }
}

data class PositionResponse(
    val quote: QuoteResponse,
    val totalCurrentValue: String,
    val investments: List<InvestmentPositionDto>,
) {
    companion object {
        fun of(
            position: InvestmentPosition,
            formats: ApiFormats,
        ) = PositionResponse(
            quote = QuoteResponse.of(position.quote, formats),
            totalCurrentValue = formats.money(position.totalCurrentValue),
            investments = position.items.map { InvestmentPositionDto.of(it, formats) },
        )
    }
}

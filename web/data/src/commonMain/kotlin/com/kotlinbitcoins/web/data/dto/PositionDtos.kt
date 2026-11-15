package com.kotlinbitcoins.web.data.dto

import kotlinx.serialization.Serializable

/** `PositionResponse` of the contract (`GET /me/investments`). */
@Serializable
data class PositionDto(
    val quote: QuoteDto,
    val totalCurrentValue: String,
    val investments: List<InvestmentPositionDto>,
)

/** `InvestmentPosition` of the contract. */
@Serializable
data class InvestmentPositionDto(
    val id: String,
    val purchasedAt: String,
    val investedAmount: String,
    val purchaseQuote: String,
    val btcAmount: String,
    val variationPercent: String,
    val currentValue: String,
)

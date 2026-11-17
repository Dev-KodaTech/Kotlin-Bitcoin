@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.kotlinbitcoins.web.data.dto

import kotlinx.serialization.Serializable

/**
 * `RedemptionResponse` of the contract (`POST /me/redemptions` 201). The request is [AmountRequestDto]. [reinvestment]
 * is always present in the JSON and null when no investment was partially liquidated.
 */
@Serializable
data class RedemptionResponseDto(
    val requestId: String,
    val amount: String,
    val btcSold: String,
    val quote: String,
    val balance: String,
    val occurredAt: String,
    val liquidations: List<LiquidationDto>,
    val reinvestment: ReinvestmentDto? = null,
)

@Serializable
data class LiquidationDto(
    val investmentId: String,
    val amount: String,
    val btcAmount: String,
    val isPartial: Boolean,
)

@Serializable
data class ReinvestmentDto(
    val investmentId: String,
    val btcAmount: String,
    val investedAmount: String,
    val purchaseQuote: String,
    val purchasedAt: String,
)

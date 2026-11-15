package com.kotlinbitcoins.web.data.dto

import kotlinx.serialization.Serializable

/** `PurchaseResponse` of the contract (`POST /me/purchases` 201). The request is [AmountRequestDto]. */
@Serializable
data class PurchaseResponseDto(
    val investmentId: String,
    val transactionId: String,
    val amount: String,
    val btcAmount: String,
    val quote: String,
    val purchasedAt: String,
    val balance: String,
)

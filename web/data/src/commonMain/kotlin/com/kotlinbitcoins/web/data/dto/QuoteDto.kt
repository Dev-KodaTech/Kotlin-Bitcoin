package com.kotlinbitcoins.web.data.dto

import kotlinx.serialization.Serializable

/** `Quote` of the contract: prices with the scale of the source (up to 8 decimals) and an ISO-8601 instant. */
@Serializable
data class QuoteDto(
    val buyPrice: String,
    val sellPrice: String,
    val quotedAt: String,
)

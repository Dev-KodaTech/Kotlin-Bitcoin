@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.kotlinbitcoins.web.data.dto

import kotlinx.serialization.Serializable

/** `DailyVolumeResponse` of the contract (`GET /market/daily-volume`): date yyyy-MM-dd and two `Btc`. */
@Serializable
data class DailyVolumeDto(
    val date: String,
    val boughtBtc: String,
    val soldBtc: String,
)

/** `QuoteHistoryResponse` of the contract (`GET /quotes/history`). */
@Serializable
data class QuoteHistoryDto(
    val points: List<HistoryPointDto>,
)

@Serializable
data class HistoryPointDto(
    val slotAt: String,
    val buyPrice: String,
    val sellPrice: String,
)

@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.kotlinbitcoins.web.data.dto

import kotlinx.serialization.Serializable

/** `StatementResponse` of the contract (`GET /me/statement`). */
@Serializable
data class StatementDto(
    val from: String,
    val to: String,
    val entries: List<StatementEntryDto>,
)

/** `StatementEntry` of the contract; the nullable fields are `null` when not applicable. */
@Serializable
data class StatementEntryDto(
    val id: String,
    val type: String,
    val amount: String,
    val btcAmount: String? = null,
    val quote: String? = null,
    val investmentId: String? = null,
    val requestId: String? = null,
    val occurredAt: String,
)

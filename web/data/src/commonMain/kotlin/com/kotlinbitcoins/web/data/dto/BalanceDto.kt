package com.kotlinbitcoins.web.data.dto

import kotlinx.serialization.Serializable

/** `BalanceResponse` of the contract (`GET /me/balance`). */
@Serializable
data class BalanceDto(
    val balance: String,
)

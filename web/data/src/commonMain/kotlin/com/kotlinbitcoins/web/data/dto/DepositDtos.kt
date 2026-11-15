package com.kotlinbitcoins.web.data.dto

import kotlinx.serialization.Serializable

/** `AmountRequest` of the contract: the amount as the `MoneyInput` string ("1234.56"). */
@Serializable
data class AmountRequestDto(
    val amount: String,
)

/** `DepositResponse` of the contract (`POST /me/deposits` 201). */
@Serializable
data class DepositResponseDto(
    val transactionId: String,
    val amount: String,
    val balance: String,
    val occurredAt: String,
)

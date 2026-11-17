package com.kotlinbitcoins.web.core.model

import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.time.ApiInstant

/** Deposit credited by `POST /me/deposits` (201). Every value is shown as received from the API (Principle II). */
data class DepositResult(
    val transactionId: String,
    val amount: Money,
    val balance: Money,
    val occurredAt: ApiInstant,
)

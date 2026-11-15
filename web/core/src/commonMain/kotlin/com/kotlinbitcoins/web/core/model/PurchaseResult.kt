package com.kotlinbitcoins.web.core.model

import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.ApiInstant

/**
 * Purchase made by `POST /me/purchases` (201). Every value is shown as received from the API, never an estimate
 * (FR-017, Principle II).
 */
data class PurchaseResult(
    val investmentId: String,
    val transactionId: String,
    val amount: Money,
    val btcAmount: BtcAmount,
    val quote: QuotePrice,
    val purchasedAt: ApiInstant,
    val balance: Money,
)

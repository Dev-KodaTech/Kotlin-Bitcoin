package com.kotlinbitcoins.web.core.model

import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.money.VariationPercent
import com.kotlinbitcoins.web.core.time.ApiInstant

/** One open investment of the position. Every value comes from the API without change (Principle II). */
data class InvestmentPosition(
    val id: String,
    val purchasedAt: ApiInstant,
    val investedAmount: Money,
    val purchaseQuote: QuotePrice,
    val btcAmount: BtcAmount,
    val variationPercent: VariationPercent,
    val currentValue: Money,
)

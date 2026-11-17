package com.kotlinbitcoins.web.core.model

import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.ApiInstant

/**
 * Redemption made by `POST /me/redemptions` (201). Every value is shown as received from the API, never an estimate
 * (FR-024, Principle II): the [liquidations] keep the API order (FIFO) and [reinvestment] is null when no investment
 * was partially liquidated.
 */
data class RedemptionResult(
    val requestId: String,
    val amount: Money,
    val btcSold: BtcAmount,
    val quote: QuotePrice,
    val balance: Money,
    val occurredAt: ApiInstant,
    val liquidations: List<Liquidation>,
    val reinvestment: Reinvestment?,
)

/** One investment liquidated by a redemption, in total or in part ([isPartial]). */
data class Liquidation(
    val investmentId: String,
    val amount: Money,
    val btcAmount: BtcAmount,
    val isPartial: Boolean,
)

/** The residual BTC of a partial liquidation, still invested with the quote and date of the original purchase. */
data class Reinvestment(
    val investmentId: String,
    val btcAmount: BtcAmount,
    val investedAmount: Money,
    val purchaseQuote: QuotePrice,
    val purchasedAt: ApiInstant,
)

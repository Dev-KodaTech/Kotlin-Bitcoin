package com.kotlinbitcoins.web.state.support

import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.DepositResult
import com.kotlinbitcoins.web.core.model.InvestmentPosition
import com.kotlinbitcoins.web.core.model.Liquidation
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.core.model.PurchaseResult
import com.kotlinbitcoins.web.core.model.Quote
import com.kotlinbitcoins.web.core.model.RedemptionResult
import com.kotlinbitcoins.web.core.model.Reinvestment
import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.money.VariationPercent
import com.kotlinbitcoins.web.core.time.ApiInstant
import kotlin.test.fail

fun <E, T> Outcome<E, T>.successValue(): T =
    when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> fail("Expected Success, got Failure($error)")
    }

fun money(raw: String): Money = Money.parseApi(raw).successValue()

fun quote(
    buy: String = "400000.00",
    sell: String = "401000.00",
    at: String = "2026-11-09T08:10:00-03:00",
): Quote =
    Quote(
        buyPrice = QuotePrice.parseApi(buy).successValue(),
        sellPrice = QuotePrice.parseApi(sell).successValue(),
        quotedAt = ApiInstant.parseApi(at).successValue(),
    )

fun position(
    total: String,
    investments: Int,
    quote: Quote = quote(),
): Position =
    Position(
        quote = quote,
        totalCurrentValue = money(total),
        investments =
            List(investments) { index ->
                InvestmentPosition(
                    id = "investment-$index",
                    purchasedAt = ApiInstant.parseApi("2026-11-01T10:15:00-03:00").successValue(),
                    investedAmount = money("100.00"),
                    purchaseQuote = QuotePrice.parseApi("400000.00").successValue(),
                    btcAmount = BtcAmount.parseApi("0.00025000").successValue(),
                    variationPercent = VariationPercent.parseApi("0.00").successValue(),
                    currentValue = money("100.00"),
                )
            },
    )

fun depositResult(
    amount: String = "100.00",
    balance: String = "150.00",
    at: String = "2026-11-09T08:10:00-03:00",
): DepositResult =
    DepositResult(
        transactionId = "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        amount = money(amount),
        balance = money(balance),
        occurredAt = ApiInstant.parseApi(at).successValue(),
    )

fun purchaseResult(
    amount: String = "400.00",
    btc: String = "0.00100000",
    quote: String = "400000.00",
    balance: String = "600.00",
    at: String = "2026-11-09T08:10:00-03:00",
): PurchaseResult =
    PurchaseResult(
        investmentId = "0b6f5c1e-2a3d-4e5f-8a9b-1c2d3e4f5a01",
        transactionId = "9a1f0c2e-3b4d-4c5e-8f6a-7b8c9d0e1f2a",
        amount = money(amount),
        btcAmount = BtcAmount.parseApi(btc).successValue(),
        quote = QuotePrice.parseApi(quote).successValue(),
        purchasedAt = ApiInstant.parseApi(at).successValue(),
        balance = money(balance),
    )

/** A redemption of R$ 500,00 that liquidates one investment in total and one in part, with [withReinvestment]. */
fun redemptionResult(
    amount: String = "500.00",
    balance: String = "900.00",
    withReinvestment: Boolean = true,
): RedemptionResult =
    RedemptionResult(
        requestId = "5c3e1a2b-4d5f-4a6b-8c7d-9e0f1a2b3c4d",
        amount = money(amount),
        btcSold = BtcAmount.parseApi("0.00125000").successValue(),
        quote = QuotePrice.parseApi("400000.00").successValue(),
        balance = money(balance),
        occurredAt = ApiInstant.parseApi("2026-11-09T09:00:00-03:00").successValue(),
        liquidations =
            listOf(
                Liquidation("investment-0", money("400.00"), BtcAmount.parseApi("0.00100000").successValue(), false),
                Liquidation("investment-1", money("100.00"), BtcAmount.parseApi("0.00025000").successValue(), true),
            ),
        reinvestment =
            if (withReinvestment) {
                Reinvestment(
                    investmentId = "investment-2",
                    btcAmount = BtcAmount.parseApi("0.00025000").successValue(),
                    investedAmount = money("100.00"),
                    purchaseQuote = QuotePrice.parseApi("400000.00").successValue(),
                    purchasedAt = ApiInstant.parseApi("2026-11-09T08:20:00-03:00").successValue(),
                )
            } else {
                null
            },
    )

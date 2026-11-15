package com.kotlinbitcoins.web.state.support

import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.InvestmentPosition
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.core.model.Quote
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

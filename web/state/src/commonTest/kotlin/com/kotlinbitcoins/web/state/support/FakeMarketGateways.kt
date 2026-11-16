package com.kotlinbitcoins.web.state.support

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.DailyVolume
import com.kotlinbitcoins.web.core.model.HistoryPoint
import com.kotlinbitcoins.web.core.model.Quote
import com.kotlinbitcoins.web.core.model.QuoteHistory
import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.CalendarDate
import com.kotlinbitcoins.web.state.port.MarketGateway
import com.kotlinbitcoins.web.state.port.QuoteGateway
import kotlinx.coroutines.CompletableDeferred

/**
 * The volume of [MarketGateway] and the history of [QuoteGateway], with mutable results and call counters. With a
 * gate set, the next calls wait for it, so a test can see the state while a load is in flight.
 */
class FakeMarketGateways :
    MarketGateway,
    QuoteGateway {
    var volumeResult: Outcome<ApiError, DailyVolume> = Outcome.Success(sampleVolume())
    var historyResult: Outcome<ApiError, QuoteHistory> = Outcome.Success(sampleHistory())
    var gate: CompletableDeferred<Unit>? = null

    var volumeCalls = 0
        private set
    var historyCalls = 0
        private set

    override suspend fun dailyVolume(): Outcome<ApiError, DailyVolume> {
        volumeCalls++
        gate?.await()
        return volumeResult
    }

    override suspend fun history(): Outcome<ApiError, QuoteHistory> {
        historyCalls++
        gate?.await()
        return historyResult
    }

    override suspend fun current(): Outcome<ApiError, Quote> = error("the Mercado does not read the current quote")
}

fun sampleVolume(
    bought: String = "0.00300000",
    sold: String = "0.00050000",
): DailyVolume =
    DailyVolume(
        date = requireNotNull(CalendarDate.parseIso("2026-11-09")),
        boughtBtc = BtcAmount.parseApi(bought).successValue(),
        soldBtc = BtcAmount.parseApi(sold).successValue(),
    )

/** Three points with a missing slot between the second and the third. */
fun sampleHistory(): QuoteHistory =
    QuoteHistory(
        listOf("08:00" to "400000.00", "08:10" to "400100.00", "08:30" to "400050.00").map { (time, price) ->
            HistoryPoint(
                slotAt = instant("2026-11-09T$time:00-03:00"),
                buyPrice = QuotePrice.parseApi(price).successValue(),
                sellPrice = QuotePrice.parseApi(price).successValue(),
            )
        },
    )

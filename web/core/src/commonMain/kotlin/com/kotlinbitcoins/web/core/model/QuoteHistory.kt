package com.kotlinbitcoins.web.core.model

import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.ApiInstant

/** Quote history of the last 24 h (`GET /quotes/history`): one point per recorded 10 min slot, in time order. */
data class QuoteHistory(
    val points: List<HistoryPoint>,
)

/** Buy and sell prices of the 10 min slot starting at [slotAt]. Slots without record are simply absent. */
data class HistoryPoint(
    val slotAt: ApiInstant,
    val buyPrice: QuotePrice,
    val sellPrice: QuotePrice,
)

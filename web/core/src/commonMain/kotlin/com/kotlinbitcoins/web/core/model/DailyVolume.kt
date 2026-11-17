package com.kotlinbitcoins.web.core.model

import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.time.CalendarDate

/** BTC bought and sold today by every client, Brasília day ([date] as returned by the API) (FR-031). */
data class DailyVolume(
    val date: CalendarDate,
    val boughtBtc: BtcAmount,
    val soldBtc: BtcAmount,
)

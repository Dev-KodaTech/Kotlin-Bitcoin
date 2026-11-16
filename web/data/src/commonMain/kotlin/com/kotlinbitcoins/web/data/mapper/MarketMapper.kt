package com.kotlinbitcoins.web.data.mapper

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.error.flatMap
import com.kotlinbitcoins.web.core.error.map
import com.kotlinbitcoins.web.core.error.toOutcome
import com.kotlinbitcoins.web.core.model.DailyVolume
import com.kotlinbitcoins.web.core.model.HistoryPoint
import com.kotlinbitcoins.web.core.model.QuoteHistory
import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.ApiInstant
import com.kotlinbitcoins.web.core.time.CalendarDate
import com.kotlinbitcoins.web.data.dto.DailyVolumeDto
import com.kotlinbitcoins.web.data.dto.HistoryPointDto
import com.kotlinbitcoins.web.data.dto.QuoteHistoryDto

/** Market DTOs → models; any value outside the contract makes the whole response [ApiError.Unexpected] (FR-041). */
object MarketMapper {
    fun toDailyVolume(dto: DailyVolumeDto): Outcome<ApiError, DailyVolume> =
        CalendarDate.parseIso(dto.date).toOutcome(ApiError.Unexpected).flatMap { date ->
            BtcAmount.parseApi(dto.boughtBtc).flatMap { bought ->
                BtcAmount.parseApi(dto.soldBtc).map { sold -> DailyVolume(date, bought, sold) }
            }
        }

    /** Keeps the order of the API (chronological); nothing is sorted, filled or dropped. */
    fun toQuoteHistory(dto: QuoteHistoryDto): Outcome<ApiError, QuoteHistory> {
        val points = ArrayList<HistoryPoint>(dto.points.size)
        for (pointDto in dto.points) {
            when (val point = toPoint(pointDto)) {
                is Outcome.Success -> points += point.value
                is Outcome.Failure -> return point
            }
        }
        return Outcome.Success(QuoteHistory(points))
    }

    private fun toPoint(dto: HistoryPointDto): Outcome<ApiError, HistoryPoint> =
        ApiInstant.parseApi(dto.slotAt).flatMap { slotAt ->
            QuotePrice.parseApi(dto.buyPrice).flatMap { buy ->
                QuotePrice.parseApi(dto.sellPrice).map { sell -> HistoryPoint(slotAt, buy, sell) }
            }
        }
}

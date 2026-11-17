package com.kotlinbitcoins.web.data.mapper

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.error.flatMap
import com.kotlinbitcoins.web.core.error.map
import com.kotlinbitcoins.web.core.model.Quote
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.ApiInstant
import com.kotlinbitcoins.web.data.dto.QuoteDto

/** [QuoteDto] → [Quote]; a price or instant outside the contract pattern is [ApiError.Unexpected] (FR-041). */
object QuoteMapper {
    fun toQuote(dto: QuoteDto): Outcome<ApiError, Quote> =
        QuotePrice.parseApi(dto.buyPrice).flatMap { buy ->
            QuotePrice.parseApi(dto.sellPrice).flatMap { sell ->
                ApiInstant.parseApi(dto.quotedAt).map { at -> Quote(buyPrice = buy, sellPrice = sell, quotedAt = at) }
            }
        }
}

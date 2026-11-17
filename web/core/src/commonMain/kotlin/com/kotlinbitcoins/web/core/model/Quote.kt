package com.kotlinbitcoins.web.core.model

import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.ApiInstant

/** Current bitcoin quote (`GET /quotes/current`). */
data class Quote(
    val buyPrice: QuotePrice,
    val sellPrice: QuotePrice,
    val quotedAt: ApiInstant,
)

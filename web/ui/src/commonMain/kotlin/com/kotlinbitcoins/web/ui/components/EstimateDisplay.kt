package com.kotlinbitcoins.web.ui.components

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.ApiInstant

/** What an [EstimateCard] shows: the estimate, or why there is none. */
sealed interface EstimateDisplay {
    data object Loading : EstimateDisplay

    /** QUOTE_UNAVAILABLE: no estimate and no old values (US4-7); the confirmation stays enabled. */
    data object QuoteUnavailable : EstimateDisplay

    data class Failed(
        val error: ApiError,
    ) : EstimateDisplay

    /** The estimate with the price and the instant of the quote it used (US4-1). */
    data class Ready(
        val btc: BtcAmount,
        val price: QuotePrice,
        val quotedAt: ApiInstant,
        /** Warnings below the notice, e.g. "below 1 satoshi" (FR-016); they never block the confirmation. */
        val warnings: List<String> = emptyList(),
    ) : EstimateDisplay
}

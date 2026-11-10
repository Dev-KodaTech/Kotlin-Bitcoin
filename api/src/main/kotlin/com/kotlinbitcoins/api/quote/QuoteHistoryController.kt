package com.kotlinbitcoins.api.quote

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.application.quote.GetQuoteHistory
import com.kotlinbitcoins.domain.quote.QuoteSnapshot
import com.kotlinbitcoins.domain.shared.fold
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** `slotAt` is the 10 minute mark in the business zone; prices keep the scale of the source (FR-027, FR-028). */
data class QuoteHistoryPoint(
    val slotAt: String,
    val buyPrice: String,
    val sellPrice: String,
)

data class QuoteHistoryResponse(
    val points: List<QuoteHistoryPoint>,
) {
    companion object {
        fun of(
            snapshots: List<QuoteSnapshot>,
            formats: ApiFormats,
        ) = QuoteHistoryResponse(
            snapshots.map {
                QuoteHistoryPoint(
                    slotAt = formats.instant(it.slotAt),
                    buyPrice = formats.quote(it.buyPrice),
                    sellPrice = formats.quote(it.sellPrice),
                )
            },
        )
    }
}

/** Quote history of the last 24 hours in 10 minute points; slots that were not captured are omitted (US10). */
@RestController
class QuoteHistoryController(
    private val getQuoteHistory: GetQuoteHistory,
    private val formats: ApiFormats,
    private val problems: ProblemDetailsMapper,
) {
    @GetMapping("/api/v1/quotes/history")
    fun history(): ResponseEntity<*> =
        getQuoteHistory
            .execute()
            .fold(
                onFailure = problems::quoteHistoryError,
                onSuccess = { ResponseEntity.ok(QuoteHistoryResponse.of(it, formats)) },
            )
}

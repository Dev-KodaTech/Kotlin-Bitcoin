package com.kotlinbitcoins.api.quote

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.api.openapi.PROBLEM_JSON
import com.kotlinbitcoins.api.openapi.ProblemBody
import com.kotlinbitcoins.application.quote.GetQuoteHistory
import com.kotlinbitcoins.domain.quote.QuoteSnapshot
import com.kotlinbitcoins.domain.shared.fold
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
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
    @ApiResponse(
        responseCode = "200",
        description = "Até 144 pontos em ordem cronológica",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = QuoteHistoryResponse::class),
            ),
        ],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente, malformado, adulterado ou expirado",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemBody::class))],
    )
    @GetMapping("/api/v1/quotes/history")
    fun history(): ResponseEntity<*> =
        getQuoteHistory
            .execute()
            .fold(
                onFailure = problems::quoteHistoryError,
                onSuccess = { ResponseEntity.ok(QuoteHistoryResponse.of(it, formats)) },
            )
}

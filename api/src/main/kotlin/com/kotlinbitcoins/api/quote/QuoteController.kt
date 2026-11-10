package com.kotlinbitcoins.api.quote

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.api.openapi.PROBLEM_JSON
import com.kotlinbitcoins.api.openapi.ProblemBody
import com.kotlinbitcoins.application.quote.GetCurrentQuote
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.fold
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Prices keep the scale of the source; `quotedAt` is the instant the quote was obtained (FR-011, FR-012a). */
data class QuoteResponse(
    val buyPrice: String,
    val sellPrice: String,
    val quotedAt: String,
) {
    companion object {
        fun of(
            quote: Quote,
            formats: ApiFormats,
        ) = QuoteResponse(
            buyPrice = formats.quote(quote.buyPrice),
            sellPrice = formats.quote(quote.sellPrice),
            quotedAt = formats.instant(quote.quotedAt),
        )
    }
}

/** Current BTC quote in reais, with at most 60 s of age (US4, FR-011, FR-012). */
@RestController
class QuoteController(
    private val getCurrentQuote: GetCurrentQuote,
    private val formats: ApiFormats,
    private val problems: ProblemDetailsMapper,
) {
    @ApiResponse(
        responseCode = "200",
        description = "Cotação com até 60 s de idade",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = QuoteResponse::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente, malformado, adulterado ou expirado",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemBody::class))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "Sem cotação válida com até 60 s",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemBody::class))],
    )
    @GetMapping("/api/v1/quotes/current")
    fun current(): ResponseEntity<*> =
        getCurrentQuote
            .execute()
            .fold(
                onFailure = problems::quoteError,
                onSuccess = { ResponseEntity.ok(QuoteResponse.of(it, formats)) },
            )
}

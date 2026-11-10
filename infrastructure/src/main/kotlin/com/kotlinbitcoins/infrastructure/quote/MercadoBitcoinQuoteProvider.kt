package com.kotlinbitcoins.infrastructure.quote

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.QuoteProvider
import com.kotlinbitcoins.application.port.QuoteProviderError
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.net.URI
import java.time.Instant

/**
 * [QuoteProvider] over the Mercado Bitcoin v1 ticker (R-06): `ticker.sell` is the sell price (customer purchases)
 * and `ticker.buy` the buy price (redemptions and position).
 *
 * Prices are read as [BigDecimal] straight from the JSON text, never through `double`, and keep the scale the
 * source sent (FR-011). `quotedAt` is the instant the answer is received, not `ticker.date` (FR-012a). Any
 * exception, status other than 2xx, invalid JSON or unusable price becomes a [QuoteProviderError]; nothing is thrown.
 */
class MercadoBitcoinQuoteProvider(
    properties: QuoteProviderProperties,
    private val clock: Clock,
) : QuoteProvider {
    private val uri: URI = URI.create(properties.url)

    private val client: RestClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(properties.connectTimeout)
                    setReadTimeout(properties.readTimeout)
                },
            ).build()

    // Decimals must stay decimals: a JSON number such as 123456789.12345679 is read as BigDecimal, not as double,
    // and exact BigDecimals keep trailing zeros (400000.50 stays scale 2 instead of becoming 400000.5).
    private val json: ObjectMapper =
        ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))

    override fun fetchCurrent(): Outcome<QuoteProviderError, Quote> =
        try {
            val body =
                client
                    .get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String::class.java)
            val receivedAt = clock.now()
            parse(body, receivedAt)
        } catch (exception: RestClientException) {
            failure("request failed: ${exception.javaClass.simpleName}")
        }

    private fun parse(
        body: String?,
        receivedAt: Instant,
    ): Outcome<QuoteProviderError, Quote> {
        val ticker =
            try {
                body?.let { json.readTree(it) }?.path(TICKER)
            } catch (exception: JsonProcessingException) {
                return failure("invalid JSON: ${exception.javaClass.simpleName}")
            }
        val buy = ticker?.let { price(it.path(BUY)) }
        val sell = ticker?.let { price(it.path(SELL)) }
        return if (buy == null || sell == null) {
            failure("missing or unusable price")
        } else {
            Outcome.Success(Quote(buyPrice = buy, sellPrice = sell, quotedAt = receivedAt))
        }
    }

    /** A usable price, or `null` when the field is absent, not a decimal, zero, negative or too precise. */
    private fun price(node: JsonNode): QuotePrice? {
        val decimal =
            when {
                node.isTextual -> node.textValue().toBigDecimalOrNull()
                node.isNumber -> node.decimalValue()
                else -> null
            }
        return decimal?.let { (QuotePrice.of(it) as? Outcome.Success)?.value }
    }

    private fun failure(reason: String): Outcome.Failure<QuoteProviderError> {
        logger.warn("Quote source unavailable: {}", reason)
        return Outcome.Failure(QuoteProviderError(reason))
    }

    private companion object {
        const val TICKER = "ticker"
        const val BUY = "buy"
        const val SELL = "sell"
        private val logger = LoggerFactory.getLogger(MercadoBitcoinQuoteProvider::class.java)
    }
}

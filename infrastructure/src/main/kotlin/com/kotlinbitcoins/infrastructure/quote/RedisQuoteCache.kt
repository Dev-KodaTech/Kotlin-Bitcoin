package com.kotlinbitcoins.infrastructure.quote

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.kotlinbitcoins.application.port.QuoteCache
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * [QuoteCache] on Redis, shared by every replica (R-06). The value is JSON with the prices as strings (so the
 * source's exact scale survives) and `quotedAt` in ISO-8601; it lives under `quote:current` for 60 s, the longest a
 * quote can still be served.
 *
 * Best-effort: any Redis failure or unreadable value behaves as an empty cache. [get] answers `null` and [put] only
 * logs, so a cache problem never fails an operation.
 */
class RedisQuoteCache(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : QuoteCache {
    // The cache must never fail an operation, whatever Redis or the client library throws (R-06).
    @Suppress("TooGenericExceptionCaught")
    override fun get(): Quote? =
        try {
            redis.opsForValue().get(KEY)?.let(::decode)
        } catch (exception: RuntimeException) {
            logger.warn("Quote cache read failed: {}", exception.javaClass.simpleName)
            null
        }

    @Suppress("TooGenericExceptionCaught")
    override fun put(quote: Quote) {
        try {
            val value =
                objectMapper.writeValueAsString(
                    mapOf(
                        BUY_PRICE to quote.buyPrice.value.toPlainString(),
                        SELL_PRICE to quote.sellPrice.value.toPlainString(),
                        QUOTED_AT to quote.quotedAt.toString(),
                    ),
                )
            redis.opsForValue().set(KEY, value, TTL)
        } catch (exception: RuntimeException) {
            logger.warn("Quote cache write failed: {}", exception.javaClass.simpleName)
        }
    }

    /** The quote in [value], or `null` (logged) when it is not a quote this cache wrote. */
    private fun decode(value: String): Quote? =
        try {
            val node = objectMapper.readTree(value)
            val buy = price(node.path(BUY_PRICE).textValue())
            val sell = price(node.path(SELL_PRICE).textValue())
            val quotedAt = node.path(QUOTED_AT).textValue()?.let(Instant::parse)
            if (buy == null || sell == null || quotedAt == null) {
                logger.warn("Quote cache holds an unreadable value; ignoring it")
                null
            } else {
                Quote(buy, sell, quotedAt)
            }
        } catch (exception: JsonProcessingException) {
            logger.warn("Quote cache holds an unreadable value: {}", exception.javaClass.simpleName)
            null
        } catch (exception: DateTimeParseException) {
            logger.warn("Quote cache holds an unreadable value: {}", exception.javaClass.simpleName)
            null
        }

    private fun price(text: String?): QuotePrice? =
        text?.toBigDecimalOrNull()?.let { (QuotePrice.of(it) as? Outcome.Success)?.value }

    private companion object {
        const val KEY = "quote:current"
        const val BUY_PRICE = "buyPrice"
        const val SELL_PRICE = "sellPrice"
        const val QUOTED_AT = "quotedAt"
        val TTL: Duration = Duration.ofSeconds(60)
        private val logger = LoggerFactory.getLogger(RedisQuoteCache::class.java)
    }
}

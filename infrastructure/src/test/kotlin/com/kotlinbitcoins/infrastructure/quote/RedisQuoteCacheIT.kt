package com.kotlinbitcoins.infrastructure.quote

import com.fasterxml.jackson.databind.ObjectMapper
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import io.lettuce.core.ClientOptions
import io.lettuce.core.SocketOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class RedisQuoteCacheIT {
    private val quote = Quote(price("399000.12345678"), price("400000.00"), Instant.parse("2026-11-09T11:10:00Z"))

    private val cache = RedisQuoteCache(template, ObjectMapper())

    @BeforeEach
    fun flush() {
        template.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
    }

    @Test
    fun `put and get preserve the exact scale of both prices and the quotedAt`() {
        cache.put(quote)

        val cached = cache.get()

        assertThat(cached).isEqualTo(quote)
        assertThat(cached!!.buyPrice.value.toPlainString()).isEqualTo("399000.12345678")
        assertThat(cached.sellPrice.value.toPlainString()).isEqualTo("400000.00")
        assertThat(cached.quotedAt).isEqualTo(Instant.parse("2026-11-09T11:10:00Z"))
    }

    @Test
    fun `the stored value carries prices as strings and quotedAt in ISO format`() {
        cache.put(quote)

        val json = ObjectMapper().readTree(template.opsForValue().get("quote:current"))

        assertThat(json["buyPrice"].isTextual).isTrue()
        assertThat(json["buyPrice"].asText()).isEqualTo("399000.12345678")
        assertThat(json["sellPrice"].asText()).isEqualTo("400000.00")
        assertThat(json["quotedAt"].asText()).isEqualTo("2026-11-09T11:10:00Z")
    }

    @Test
    fun `the key expires with a TTL of 60 seconds`() {
        cache.put(quote)

        val ttl = template.getExpire("quote:current")

        assertThat(ttl).isBetween(55L, 60L)
    }

    @Test
    fun `a second put replaces the first and renews the TTL`() {
        cache.put(quote)
        val newer = Quote(price("1.00"), price("2.00"), Instant.parse("2026-11-09T11:11:00Z"))

        cache.put(newer)

        assertThat(cache.get()).isEqualTo(newer)
    }

    @Test
    fun `get without a key is null`() {
        assertThat(cache.get()).isNull()
    }

    @Test
    fun `a corrupted value is treated as an empty cache`() {
        listOf("not json", "{}", """{"buyPrice":"abc","sellPrice":"1","quotedAt":"2026-11-09T11:10:00Z"}""")
            .forEach { corrupted ->
                template.opsForValue().set("quote:current", corrupted)

                assertThat(cache.get()).describedAs(corrupted).isNull()
            }
    }

    @Test
    fun `when Redis is down get is null and put does not throw`() {
        val down = unreachableTemplate()
        val brokenCache = RedisQuoteCache(down, ObjectMapper())

        assertThat(brokenCache.get()).isNull()
        brokenCache.put(quote)
    }

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value

    private fun unreachableTemplate(): StringRedisTemplate {
        val factory = connectionFactory("localhost", 1)
        return StringRedisTemplate(factory)
    }

    private companion object {
        const val REDIS_PORT = 6379
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7")).withExposedPorts(REDIS_PORT)
        lateinit var factory: LettuceConnectionFactory
        lateinit var template: StringRedisTemplate

        fun connectionFactory(
            host: String,
            port: Int,
        ): LettuceConnectionFactory {
            val clientConfig =
                LettuceClientConfiguration
                    .builder()
                    .commandTimeout(Duration.ofMillis(500))
                    .clientOptions(
                        ClientOptions
                            .builder()
                            .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(500)).build())
                            .build(),
                    ).build()
            return LettuceConnectionFactory(RedisStandaloneConfiguration(host, port), clientConfig).apply {
                afterPropertiesSet()
            }
        }

        @JvmStatic
        @BeforeAll
        fun start() {
            redis.start()
            factory = connectionFactory(redis.host, redis.getMappedPort(REDIS_PORT))
            template = StringRedisTemplate(factory).apply { afterPropertiesSet() }
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            factory.destroy()
            redis.stop()
        }
    }
}

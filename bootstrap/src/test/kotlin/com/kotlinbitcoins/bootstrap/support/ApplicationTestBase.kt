package com.kotlinbitcoins.bootstrap.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import java.time.Instant

/**
 * Base for tests over the fully assembled application: `@SpringBootTest` + MockMvc, singleton `postgres:16`,
 * `redis:7` and `rabbitmq:3` containers, a WireMock server standing in for the quote source, and a [MutableClock].
 * Every test starts with empty tables, an empty cache, no WireMock stubs and the clock at [DEFAULT_NOW].
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApplicationTestBase.TestClockConfiguration::class)
abstract class ApplicationTestBase {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    protected lateinit var clock: MutableClock

    @BeforeEach
    fun resetState() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE transactions, investments, accounts, quote_history, outbox_events, shedlock CASCADE",
        )
        redis.execInContainer("redis-cli", "FLUSHALL")
        quoteSource.resetAll()
        clock.set(DEFAULT_NOW)
    }

    @TestConfiguration
    class TestClockConfiguration {
        @Bean
        @Primary
        fun mutableClock(): MutableClock = MutableClock(DEFAULT_NOW)
    }

    companion object {
        val DEFAULT_NOW: Instant = Instant.parse("2026-11-09T11:10:00Z")

        private const val REDIS_PORT = 6379

        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:16"))
        val redis: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("redis:7")).withExposedPorts(REDIS_PORT)
        val rabbit: RabbitMQContainer = RabbitMQContainer(DockerImageName.parse("rabbitmq:3"))
        val quoteSource = WireMockServer(wireMockConfig().dynamicPort())

        init {
            Startables.deepStart(postgres, redis, rabbit).join()
            quoteSource.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun applicationProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { "${postgres.jdbcUrl}&socketTimeout=5" }
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(REDIS_PORT) }
            registry.add("spring.rabbitmq.host", rabbit::getHost)
            registry.add("spring.rabbitmq.port", rabbit::getAmqpPort)
            registry.add("spring.rabbitmq.username", rabbit::getAdminUsername)
            registry.add("spring.rabbitmq.password", rabbit::getAdminPassword)
            registry.add("app.jwt.secret") { JwtTestTokens.SECRET }
            registry.add("quote.provider.url") { "${quoteSource.baseUrl()}/api/BTC/ticker/" }
        }
    }
}

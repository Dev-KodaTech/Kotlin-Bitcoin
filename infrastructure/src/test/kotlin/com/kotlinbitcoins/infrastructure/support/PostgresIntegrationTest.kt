package com.kotlinbitcoins.infrastructure.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Base class for persistence integration tests: a singleton `postgres:16` container shared by every test class,
 * Flyway migrations applied on startup and Hibernate in `validate` mode. Tables are emptied before each test.
 */
@SpringBootTest(classes = [InfrastructureTestApplication::class])
abstract class PostgresIntegrationTest {
    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanTables() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE transactions, investments, accounts, quote_history, outbox_events CASCADE",
        )
        releaseSchedulerLocks()
    }

    /**
     * Releases every ShedLock lock without deleting the rows: the lock provider remembers which rows it has created,
     * so a truncated `shedlock` table would make it fail to acquire any lock from then on.
     */
    private fun releaseSchedulerLocks() {
        jdbcTemplate.update("UPDATE shedlock SET lock_until = TIMESTAMP WITH TIME ZONE 'epoch'")
    }

    /** Pauses the database container while [block] runs, so adapters see an unreachable database. */
    protected fun <T> withDatabasePaused(block: () -> T): T {
        val docker = DockerClientFactory.instance().client()
        docker.pauseContainerCmd(postgres.containerId).exec()
        try {
            return block()
        } finally {
            docker.unpauseContainerCmd(postgres.containerId).exec()
        }
    }

    companion object {
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16")).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            // socketTimeout bounds queries sent while the container is paused (seconds).
            registry.add("spring.datasource.url") { "${postgres.jdbcUrl}&socketTimeout=5" }
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.hikari.connection-timeout") { "2000" }
            registry.add("spring.datasource.hikari.validation-timeout") { "1000" }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("spring.jpa.open-in-view") { "false" }
        }
    }
}

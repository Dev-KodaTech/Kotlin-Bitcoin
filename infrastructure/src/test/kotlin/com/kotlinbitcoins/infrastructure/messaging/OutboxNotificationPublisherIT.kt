package com.kotlinbitcoins.infrastructure.messaging

import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.NotificationPublisher
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.infrastructure.support.NotificationFixtures
import com.kotlinbitcoins.infrastructure.support.PostgresIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class OutboxNotificationPublisherIT : PostgresIntegrationTest() {
    @Autowired
    private lateinit var publisher: NotificationPublisher

    @Autowired
    private lateinit var runner: TransactionRunner

    private sealed interface TestError {
        data object BusinessRule : TestError

        data class Infrastructure(
            val failure: InfrastructureFailure,
        ) : TestError
    }

    @Test
    fun `publish writes the event to the outbox with no publish or send mark`() {
        val event = NotificationFixtures.depositConfirmed(amount = "100.00")

        assertThat(publisher.publish(event)).isEqualTo(Outcome.Success(Unit))

        val row =
            jdbcTemplate.queryForMap(
                "SELECT id, type, payload::text AS payload, published_at, sent_at FROM outbox_events",
            )
        assertThat(row["id"].toString()).isEqualTo(event.id.toString())
        assertThat(row["type"]).isEqualTo("DEPOSIT_CONFIRMED")
        assertThat(row["published_at"]).isNull()
        assertThat(row["sent_at"]).isNull()
        assertThat(row["payload"] as String)
            .contains("\"amount\": \"100.00\"")
            .contains("\"recipientEmail\": \"ana@example.com\"")
            .contains("\"recipientName\": \"Ana Souza\"")
    }

    @Test
    fun `the event is written in the caller's transaction and disappears with its rollback`() {
        val event = NotificationFixtures.depositConfirmed()

        val result: Outcome<TestError, Unit> =
            runner.run(TestError::Infrastructure) {
                publisher.publish(event)
                Outcome.Failure(TestError.BusinessRule)
            }

        assertThat(result).isEqualTo(Outcome.Failure(TestError.BusinessRule))
        assertThat(outboxCount()).isZero()
    }

    @Test
    fun `the event is kept when the transaction commits`() {
        val event = NotificationFixtures.depositConfirmed()

        val result: Outcome<TestError, Unit> =
            runner.run(TestError::Infrastructure) {
                publisher.publish(event)
                Outcome.Success(Unit)
            }

        assertThat(result).isEqualTo(Outcome.Success(Unit))
        assertThat(outboxCount()).isEqualTo(1)
    }

    @Test
    fun `the payload carries no password or token`() {
        publisher.publish(NotificationFixtures.depositConfirmed())

        val payload = jdbcTemplate.queryForObject("SELECT payload::text FROM outbox_events", String::class.java)!!
        assertThat(payload.lowercase()).doesNotContain("password", "token", "hash", "secret")
    }

    @Test
    fun `database down is a failure value`() {
        val result = withDatabasePaused { publisher.publish(NotificationFixtures.depositConfirmed()) }

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
    }

    private fun outboxCount() = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Int::class.java)
}

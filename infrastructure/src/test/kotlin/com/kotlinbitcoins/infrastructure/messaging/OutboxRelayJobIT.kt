package com.kotlinbitcoins.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.kotlinbitcoins.application.port.NotificationPublisher
import com.kotlinbitcoins.infrastructure.support.MessagingIntegrationTest
import com.kotlinbitcoins.infrastructure.support.NotificationFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Relay in isolation: the listener is stopped so the published messages stay in the queue to be inspected. */
class OutboxRelayJobIT : MessagingIntegrationTest() {
    @Autowired
    private lateinit var relay: OutboxRelayJob

    @Autowired
    private lateinit var publisher: NotificationPublisher

    @Autowired
    private lateinit var listeners: RabbitListenerEndpointRegistry

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun stopListener() {
        listeners.listenerContainers.forEach { it.stop() }
    }

    @AfterEach
    fun startListener() {
        listeners.listenerContainers.forEach { it.start() }
    }

    @Test
    fun `unpublished events go to the notifications exchange with routing key email and are marked published`() {
        val event = NotificationFixtures.depositConfirmed(amount = "100.00")
        publisher.publish(event)

        val published = relay.runOnce()

        assertThat(published).isEqualTo(1)
        val message = rabbitTemplate.receive(RabbitConfig.EMAIL_QUEUE, RECEIVE_TIMEOUT_MS)
        assertThat(message).isNotNull
        val body = objectMapper.readTree(message!!.body)
        assertThat(body["id"].asText()).isEqualTo(event.id.toString())
        assertThat(body["type"].asText()).isEqualTo("DEPOSIT_CONFIRMED")
        assertThat(body["amount"].asText()).isEqualTo("100.00")
        val publishedAt =
            jdbcTemplate.queryForObject(
                "SELECT published_at FROM outbox_events WHERE id = ?",
                Any::class.java,
                event.id,
            )
        assertThat(publishedAt).isNotNull
        val sentAt =
            jdbcTemplate.queryForObject(
                "SELECT sent_at FROM outbox_events WHERE id = ?",
                Any::class.java,
                event.id,
            )
        assertThat(sentAt).describedAs("sent_at is only set once the e-mail is sent").isNull()
    }

    @Test
    fun `an event already published is not published again`() {
        publisher.publish(NotificationFixtures.depositConfirmed())

        assertThat(relay.runOnce()).isEqualTo(1)
        assertThat(relay.runOnce()).isZero()

        assertThat(drainEventIds()).hasSize(1)
    }

    @Test
    fun `two relays running at once never publish the same event twice`() {
        val ids = (1..EVENTS).map { UUID.randomUUID() }
        ids.forEach { publisher.publish(NotificationFixtures.depositConfirmed(id = it)) }

        val runs = (1..2).map { CompletableFuture.supplyAsync { drainRelay() } }
        runs.forEach { it.join() }

        val received = drainEventIds()
        assertThat(received).hasSize(EVENTS).doesNotHaveDuplicates()
        assertThat(received).containsExactlyInAnyOrderElementsOf(ids.map(UUID::toString))
        val unpublished =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL",
                Int::class.java,
            )
        assertThat(unpublished).isZero()
    }

    @Test
    fun `when the broker is unreachable events stay unpublished for the next run`() {
        publisher.publish(NotificationFixtures.depositConfirmed())

        listeners.listenerContainers.forEach { it.stop() }
        val published = withBrokerRefusing { relay.runOnce() }

        assertThat(published).isZero()
        val unpublished =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL",
                Int::class.java,
            )
        assertThat(unpublished).isEqualTo(1)
    }

    private fun drainRelay() {
        while (relay.runOnce() > 0) {
            // keep going until nothing is left to publish
        }
    }

    private fun drainEventIds(): List<String> =
        generateSequence { rabbitTemplate.receive(RabbitConfig.EMAIL_QUEUE, RECEIVE_TIMEOUT_MS) }
            .map { objectMapper.readTree(it.body)["id"].asText() }
            .toList()

    /** Makes publishing fail by sending to an exchange that does not exist: the broker closes the channel. */
    private fun <T> withBrokerRefusing(block: () -> T): T {
        rabbitAdmin.deleteExchange(RabbitConfig.EXCHANGE)
        try {
            return block()
        } finally {
            rabbitAdmin.initialize()
        }
    }

    private companion object {
        const val EVENTS = 40
        const val RECEIVE_TIMEOUT_MS = 500L
    }
}

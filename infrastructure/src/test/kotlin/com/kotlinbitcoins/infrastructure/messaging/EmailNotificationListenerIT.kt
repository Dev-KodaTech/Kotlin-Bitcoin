package com.kotlinbitcoins.infrastructure.messaging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.kotlinbitcoins.application.notification.NotificationEvent
import com.kotlinbitcoins.application.port.NotificationPublisher
import com.kotlinbitcoins.infrastructure.support.MessagingIntegrationTest
import com.kotlinbitcoins.infrastructure.support.NotificationFixtures
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageBuilder
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.util.concurrent.TimeUnit

class EmailNotificationListenerIT : MessagingIntegrationTest() {
    @Autowired
    private lateinit var publisher: NotificationPublisher

    @Autowired
    private lateinit var relay: OutboxRelayJob

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val logs = ListAppender<ILoggingEvent>()
    private val listenerLogger = LoggerFactory.getLogger(EmailNotificationListener::class.java) as Logger

    @BeforeEach
    fun captureLogs() {
        logs.start()
        listenerLogger.addAppender(logs)
    }

    @AfterEach
    fun releaseLogs() {
        listenerLogger.detachAppender(logs)
        logs.stop()
    }

    @Test
    fun `a message from the queue is sent as an e-mail and the event is marked as sent`() {
        val event = NotificationFixtures.depositConfirmed(amount = "100.00")
        publisher.publish(event)
        relay.runOnce()

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(emailSender.calls).hasSize(1)
        }
        val message = emailSender.calls.single().second
        assertThat(message.recipientEmail.value).isEqualTo("ana@example.com")
        assertThat(message.recipientName).isEqualTo("Ana Souza")
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(sentAt(event)).isNotNull
        }
    }

    @Test
    fun `a duplicate message of an event already sent is not sent again`() {
        val sent = NotificationFixtures.depositConfirmed()
        val next = NotificationFixtures.depositConfirmed()
        publisher.publish(sent)
        publisher.publish(next)
        jdbcTemplate.update("UPDATE outbox_events SET sent_at = now() WHERE id = ?", sent.id)

        rabbitTemplate.send(RabbitConfig.EXCHANGE, RabbitConfig.EMAIL_ROUTING_KEY, messageOf(sent))
        rabbitTemplate.send(RabbitConfig.EXCHANGE, RabbitConfig.EMAIL_ROUTING_KEY, messageOf(next))

        await().atMost(Duration.ofSeconds(10)).untilAsserted { assertThat(emailSender.calls).isNotEmpty }
        // The queue is FIFO: once the second message was handled, the duplicate before it was too.
        assertThat(emailSender.calls).hasSize(1)
        assertThat(
            emailSender.calls
                .single()
                .second.recipientName,
        ).isEqualTo("Ana Souza")
        assertThat(sentAt(next)).isNotNull
    }

    @Test
    fun `an unreadable message goes to the DLQ and the listener keeps working`() {
        rabbitTemplate.send(
            RabbitConfig.EXCHANGE,
            RabbitConfig.EMAIL_ROUTING_KEY,
            MessageBuilder.withBody("not json".toByteArray()).build(),
        )
        val event = NotificationFixtures.depositConfirmed()
        publisher.publish(event)
        relay.runOnce()

        val dead = rabbitTemplate.receive(RabbitConfig.EMAIL_DLQ, DLQ_TIMEOUT_MS)
        assertThat(dead).isNotNull
        assertThat(String(dead!!.body)).isEqualTo("not json")
        await().atMost(Duration.ofSeconds(10)).untilAsserted { assertThat(emailSender.calls).hasSize(1) }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    fun `a failing sender is retried 5 times with doubling delays, then the message lands in the DLQ`() {
        emailSender.isFailing = true
        val event = NotificationFixtures.depositConfirmed()
        publisher.publish(event)
        relay.runOnce()

        val dead = rabbitTemplate.receive(RabbitConfig.EMAIL_DLQ, DLQ_TIMEOUT_MS)

        assertThat(dead).describedAs("message in the DLQ").isNotNull
        assertThat(emailSender.calls).hasSize(6)
        val gaps = emailSender.calls.map { it.first }.zipWithNext { a, b -> Duration.between(a, b) }
        assertThat(gaps).hasSize(5)
        listOf(1L, 2L, 4L, 8L, 16L).forEachIndexed { index, seconds ->
            assertThat(gaps[index]).isGreaterThanOrEqualTo(Duration.ofSeconds(seconds).minusMillis(TOLERANCE_MS))
        }
        assertThat(dead!!.messageProperties.getHeader<Any>("x-attempt")).isEqualTo(5)
        assertThat(retryHops(dead)).isEqualTo(5)
        assertThat(sentAt(event)).isNull()
        val failures = logs.list.filter { it.level == Level.WARN && it.formattedMessage.contains(event.id.toString()) }
        assertThat(failures).hasSize(6)
    }

    private fun retryHops(message: Message): Long {
        val deaths = message.messageProperties.getHeader<List<Map<String, Any>>>("x-death").orEmpty()
        return deaths
            .filter {
                it["queue"] == RabbitConfig.EMAIL_RETRY_QUEUE
            }.sumOf { (it["count"] as Number).toLong() }
    }

    private fun messageOf(event: NotificationEvent): Message =
        MessageBuilder
            .withBody(objectMapper.writeValueAsBytes(NotificationPayload.of(event)))
            .setContentType("application/json")
            .build()

    private fun sentAt(event: NotificationEvent): Any? =
        jdbcTemplate.queryForObject("SELECT sent_at FROM outbox_events WHERE id = ?", Any::class.java, event.id)

    private companion object {
        const val DLQ_TIMEOUT_MS = 90_000L
        const val TOLERANCE_MS = 300L
    }
}

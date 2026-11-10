package com.kotlinbitcoins.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.kotlinbitcoins.application.notification.NotificationEvent
import com.kotlinbitcoins.application.notification.SendEmailNotification
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.EmailSendError
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.infrastructure.persistence.persistenceCall
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageBuilder
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate

/**
 * Consumes `notifications.email` and sends the e-mail (R-08, FR-030, FR-031). Delivery is at-least-once; the
 * duplicates are dropped by `outbox_events.sent_at`.
 *
 * Retries are plain values, never exceptions: when the send fails, the message is republished in
 * `notifications.email.retry` with `expiration = 2^(attempt-1)` seconds and `x-attempt = attempt`, and the original
 * is acknowledged. After [MAX_RETRIES] retries the next failure lands in `notifications.email.dlq`. Every failure is
 * logged with the `eventId`. Nothing is thrown out of [onMessage]: a channel or database problem leaves the message
 * in the queue (`basicNack` with requeue).
 */
class EmailNotificationListener(
    private val sendEmailNotification: SendEmailNotification,
    private val outbox: SpringDataOutboxEventRepository,
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    private enum class Disposition { ACK, REQUEUE }

    @RabbitListener(queues = [RabbitConfig.EMAIL_QUEUE], ackMode = "MANUAL")
    fun onMessage(
        message: Message,
        channel: Channel,
    ) {
        val deliveryTag = message.messageProperties.deliveryTag
        settle(channel, deliveryTag, process(message))
    }

    private fun process(message: Message): Disposition {
        val event = parse(message) ?: return deadLetterUnreadable(message)
        return when (
            val alreadySent =
                persistenceCall(
                    "outbox.isSent",
                ) { outbox.existsByIdAndSentAtIsNotNull(event.id) }
        ) {
            is Outcome.Failure -> Disposition.REQUEUE
            is Outcome.Success -> if (alreadySent.value) Disposition.ACK else send(event, message)
        }
    }

    private fun send(
        event: NotificationEvent,
        message: Message,
    ): Disposition =
        when (val result = sendSafely(event)) {
            is Outcome.Success -> markSent(event)
            is Outcome.Failure -> scheduleRetry(event, message, result.error)
        }

    /** The sender reports failures as values; this guards the pipeline against one that breaks that contract. */
    @Suppress("TooGenericExceptionCaught")
    private fun sendSafely(event: NotificationEvent): Outcome<EmailSendError, Unit> =
        try {
            sendEmailNotification.execute(event)
        } catch (exception: RuntimeException) {
            logger.error("E-mail sender threw for eventId={}", event.id, exception)
            Outcome.Failure(EmailSendError("unexpected ${exception.javaClass.simpleName}"))
        }

    private fun markSent(event: NotificationEvent): Disposition =
        when (persistenceCall("outbox.markSent") { outbox.markSentIfNotSent(event.id, clock.now()) }) {
            is Outcome.Success -> Disposition.ACK

            // The e-mail went out but the mark was lost: redelivery may send it again (at-least-once).
            is Outcome.Failure -> Disposition.REQUEUE
        }

    private fun scheduleRetry(
        event: NotificationEvent,
        message: Message,
        error: EmailSendError,
    ): Disposition {
        val failedAttempts = (message.messageProperties.getHeader<Any>(ATTEMPT_HEADER) as? Number)?.toInt() ?: 0
        val isExhausted = failedAttempts >= MAX_RETRIES
        val nextAttempt = if (isExhausted) failedAttempts else failedAttempts + 1
        logger.warn(
            "E-mail failed for eventId={} (failure {}, {}): {}",
            event.id,
            failedAttempts + 1,
            if (isExhausted) "sent to DLQ" else "retry in ${delaySeconds(nextAttempt)}s",
            error.reason,
        )
        val target = if (isExhausted) RabbitConfig.EMAIL_DLQ else RabbitConfig.EMAIL_RETRY_QUEUE
        val expiration = if (isExhausted) null else (delaySeconds(nextAttempt) * MILLIS_PER_SECOND).toString()
        return republish(target, message, nextAttempt, expiration)
    }

    private fun deadLetterUnreadable(message: Message): Disposition {
        logger.error("Unreadable message with id={} sent to the DLQ", message.messageProperties.messageId)
        return republish(RabbitConfig.EMAIL_DLQ, message, MAX_RETRIES, expiration = null)
    }

    /** Publishes to a queue through the default exchange, with a confirm, before the original is acknowledged. */
    private fun republish(
        queue: String,
        message: Message,
        attempt: Int,
        expiration: String?,
    ): Disposition {
        val copy =
            MessageBuilder
                .fromMessage(message)
                .setHeader(ATTEMPT_HEADER, attempt)
                .setExpiration(expiration)
                .build()
        return when (rabbitTemplate.sendConfirmed("", queue, copy)) {
            is Outcome.Success -> Disposition.ACK
            is Outcome.Failure -> Disposition.REQUEUE
        }
    }

    private fun parse(message: Message): NotificationEvent? =
        try {
            objectMapper.readValue(message.body, NotificationPayload::class.java).toEvent()
        } catch (exception: java.io.IOException) {
            logger.warn("Message is not a notification payload", exception)
            null
        }

    @Suppress("TooGenericExceptionCaught")
    private fun settle(
        channel: Channel,
        deliveryTag: Long,
        disposition: Disposition,
    ) {
        try {
            when (disposition) {
                Disposition.ACK -> channel.basicAck(deliveryTag, false)
                Disposition.REQUEUE -> channel.basicNack(deliveryTag, false, true)
            }
        } catch (exception: Exception) {
            // The channel is gone; the broker redelivers the unacknowledged message on its own.
            logger.warn("Could not settle delivery {} as {}", deliveryTag, disposition, exception)
        }
    }

    private fun delaySeconds(attempt: Int): Long = 1L shl (attempt - 1)

    private companion object {
        const val ATTEMPT_HEADER = "x-attempt"
        const val MAX_RETRIES = 5
        const val MILLIS_PER_SECOND = 1_000L
        val logger = LoggerFactory.getLogger(EmailNotificationListener::class.java)
    }
}

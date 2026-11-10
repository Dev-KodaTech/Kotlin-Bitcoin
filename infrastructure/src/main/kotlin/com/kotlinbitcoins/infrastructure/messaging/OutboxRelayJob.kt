package com.kotlinbitcoins.infrastructure.messaging

import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.infrastructure.persistence.persistenceCall
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageBuilder
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.scheduling.annotation.Scheduled

/**
 * Moves committed outbox events to RabbitMQ (R-08). It runs in every replica: `FOR UPDATE SKIP LOCKED` hands each
 * run a different set of events, so no ShedLock is needed. Each event is published with a broker confirm and marked
 * `published_at` in the same transaction that holds its lock; the first publish failure stops the batch, keeps the
 * events already marked and leaves the rest for the next run.
 */
class OutboxRelayJob(
    private val outbox: SpringDataOutboxEventRepository,
    private val rabbitTemplate: RabbitTemplate,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) {
    @Scheduled(fixedDelay = RELAY_DELAY_MS)
    fun relay() {
        runOnce()
    }

    /** Publishes one batch and returns how many events it published (0 on failure). */
    fun runOnce(): Int =
        when (
            val result =
                transactionRunner.run<InfrastructureFailure, Int>({ it }) {
                    persistenceCall("outbox.relay") { publishBatch() }
                }
        ) {
            is Outcome.Success -> {
                result.value
            }

            is Outcome.Failure -> {
                logger.warn("Outbox relay run failed: {}", result.error.reason)
                0
            }
        }

    private fun publishBatch(): Int {
        var published = 0
        for (event in outbox.lockUnpublished(batchSize)) {
            val sent =
                rabbitTemplate.sendConfirmed(
                    RabbitConfig.EXCHANGE,
                    RabbitConfig.EMAIL_ROUTING_KEY,
                    messageOf(event),
                )
            if (sent is Outcome.Failure) break
            outbox.markPublished(event.id, clock.now())
            published++
        }
        return published
    }

    private fun messageOf(event: OutboxEventJpaEntity): Message =
        MessageBuilder
            .withBody(event.payload.toByteArray(Charsets.UTF_8))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setMessageId(event.id.toString())
            .build()

    private companion object {
        const val DEFAULT_BATCH_SIZE = 100
        const val RELAY_DELAY_MS = 2_000L
        val logger = LoggerFactory.getLogger(OutboxRelayJob::class.java)
    }
}

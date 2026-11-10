package com.kotlinbitcoins.infrastructure.messaging

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.kotlinbitcoins.application.notification.NotificationEvent
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.NotificationPublisher
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.flatMap
import com.kotlinbitcoins.infrastructure.persistence.persistenceCall
import org.slf4j.LoggerFactory

/**
 * [NotificationPublisher] that writes the event to `outbox_events` (R-08). The write joins the caller's transaction,
 * so the event is committed or rolled back together with the operation that produced it (FR-031).
 */
class OutboxNotificationPublisher(
    private val repository: SpringDataOutboxEventRepository,
    private val objectMapper: ObjectMapper,
) : NotificationPublisher {
    override fun publish(event: NotificationEvent): Outcome<InfrastructureFailure, Unit> =
        serialize(event).flatMap { payload ->
            persistenceCall("outbox.publish") {
                // Flush now so a database error surfaces here as a value, not at commit time.
                repository.saveAndFlush(OutboxEventJpaEntity(event.id, event.type.name, payload, event.createdAt))
                Unit
            }
        }

    private fun serialize(event: NotificationEvent): Outcome<InfrastructureFailure, String> =
        try {
            Outcome.Success(objectMapper.writeValueAsString(NotificationPayload.of(event)))
        } catch (exception: JsonProcessingException) {
            logger.warn("Could not serialize notification event {}", event.id, exception)
            Outcome.Failure(InfrastructureFailure("outbox serialization failed"))
        }

    private companion object {
        val logger = LoggerFactory.getLogger(OutboxNotificationPublisher::class.java)
    }
}

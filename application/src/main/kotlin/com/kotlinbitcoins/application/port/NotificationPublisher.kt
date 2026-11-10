package com.kotlinbitcoins.application.port

import com.kotlinbitcoins.application.notification.NotificationEvent
import com.kotlinbitcoins.domain.shared.Outcome

/**
 * Registers a notification to be delivered. The adapter writes it in the caller's transaction (Transactional
 * Outbox, R-08), so the event exists if and only if the operation that caused it is committed.
 */
fun interface NotificationPublisher {
    fun publish(event: NotificationEvent): Outcome<InfrastructureFailure, Unit>
}

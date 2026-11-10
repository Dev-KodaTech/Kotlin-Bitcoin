package com.kotlinbitcoins.infrastructure.messaging

import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.domain.shared.Outcome
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.core.RabbitTemplate

private val logger = LoggerFactory.getLogger("com.kotlinbitcoins.infrastructure.messaging")

private const val CONFIRM_TIMEOUT_MS = 5_000L

/**
 * Publishes [message] and waits for the broker's confirm. Any broker or channel error becomes a failure value, so
 * no exception leaves the messaging adapters (R-03). The confirm needs `publisher-confirm-type=correlated`.
 */
@Suppress("TooGenericExceptionCaught")
internal fun RabbitTemplate.sendConfirmed(
    exchange: String,
    routingKey: String,
    message: Message,
): Outcome<InfrastructureFailure, Unit> =
    try {
        invoke { operations ->
            operations.send(exchange, routingKey, message)
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS)
        }
        Outcome.Success(Unit)
    } catch (exception: RuntimeException) {
        logger.warn("Publishing to '{}' with key '{}' failed", exchange, routingKey, exception)
        Outcome.Failure(InfrastructureFailure("broker publish failed: ${exception.javaClass.simpleName}"))
    }

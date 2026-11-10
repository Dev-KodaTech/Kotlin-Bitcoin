package com.kotlinbitcoins.infrastructure.messaging

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Topology of the e-mail pipeline (R-08):
 *
 * - `notifications.email` receives from the exchange with routing key `email`;
 * - `notifications.email.retry` has no consumer: each message waits there for its own TTL and is then dead-lettered
 *   back to the exchange with routing key `email`, which makes the retry delay;
 * - `notifications.email.dlq` keeps the messages whose retries are exhausted.
 *
 * Retry and DLQ are reached through the default exchange by queue name, so they need no binding. Publisher confirms
 * are switched on by `spring.rabbitmq.publisher-confirm-type=correlated`; the listener acknowledges manually.
 */
@Configuration
class RabbitConfig {
    @Bean
    fun notificationsExchange(): DirectExchange = DirectExchange(EXCHANGE, true, false)

    @Bean
    fun emailQueue(): Queue = QueueBuilder.durable(EMAIL_QUEUE).build()

    @Bean
    fun emailBinding(
        emailQueue: Queue,
        notificationsExchange: DirectExchange,
    ): Binding = BindingBuilder.bind(emailQueue).to(notificationsExchange).with(EMAIL_ROUTING_KEY)

    @Bean
    fun emailRetryQueue(): Queue =
        QueueBuilder
            .durable(EMAIL_RETRY_QUEUE)
            .deadLetterExchange(EXCHANGE)
            .deadLetterRoutingKey(EMAIL_ROUTING_KEY)
            .build()

    @Bean
    fun emailDeadLetterQueue(): Queue = QueueBuilder.durable(EMAIL_DLQ).build()

    companion object {
        const val EXCHANGE = "notifications"
        const val EMAIL_ROUTING_KEY = "email"
        const val EMAIL_QUEUE = "notifications.email"
        const val EMAIL_RETRY_QUEUE = "notifications.email.retry"
        const val EMAIL_DLQ = "notifications.email.dlq"
    }
}

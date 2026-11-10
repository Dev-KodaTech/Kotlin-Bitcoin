package com.kotlinbitcoins.infrastructure.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.kotlinbitcoins.application.notification.SendEmailNotification
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.infrastructure.messaging.EmailNotificationListener
import com.kotlinbitcoins.infrastructure.messaging.OutboxRelayJob
import com.kotlinbitcoins.infrastructure.messaging.RabbitConfig
import com.kotlinbitcoins.infrastructure.messaging.SpringDataOutboxEventRepository
import com.kotlinbitcoins.infrastructure.time.SystemClock
import org.junit.jupiter.api.BeforeEach
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.utility.DockerImageName

/** Persistence tests plus a singleton `rabbitmq:3` container and the messaging beans (relay, listener, queues). */
@SpringBootTest(
    classes = [InfrastructureTestApplication::class, MessagingIntegrationTest.MessagingTestConfiguration::class],
)
abstract class MessagingIntegrationTest : PostgresIntegrationTest() {
    @Autowired
    protected lateinit var rabbitTemplate: RabbitTemplate

    @Autowired
    protected lateinit var rabbitAdmin: RabbitAdmin

    @Autowired
    protected lateinit var emailSender: ScriptedEmailSender

    @BeforeEach
    fun resetMessaging() {
        emailSender.reset()
        listOf(RabbitConfig.EMAIL_QUEUE, RabbitConfig.EMAIL_RETRY_QUEUE, RabbitConfig.EMAIL_DLQ)
            .forEach(rabbitAdmin::purgeQueue)
    }

    @TestConfiguration
    @Import(RabbitConfig::class)
    class MessagingTestConfiguration {
        @Bean
        fun clock(): Clock = SystemClock()

        @Bean
        fun scriptedEmailSender() = ScriptedEmailSender()

        @Bean
        fun sendEmailNotification(sender: ScriptedEmailSender) = SendEmailNotification(sender)

        @Bean
        fun outboxRelayJob(
            outbox: SpringDataOutboxEventRepository,
            rabbitTemplate: RabbitTemplate,
            transactionRunner: TransactionRunner,
            clock: Clock,
        ) = OutboxRelayJob(outbox, rabbitTemplate, transactionRunner, clock, BATCH_SIZE)

        @Bean
        fun emailNotificationListener(
            sendEmailNotification: SendEmailNotification,
            outbox: SpringDataOutboxEventRepository,
            rabbitTemplate: RabbitTemplate,
            objectMapper: ObjectMapper,
            clock: Clock,
        ) = EmailNotificationListener(sendEmailNotification, outbox, rabbitTemplate, objectMapper, clock)
    }

    companion object {
        const val BATCH_SIZE = 10
        private val rabbit = RabbitMQContainer(DockerImageName.parse("rabbitmq:3")).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun rabbitProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.rabbitmq.host", rabbit::getHost)
            registry.add("spring.rabbitmq.port", rabbit::getAmqpPort)
            registry.add("spring.rabbitmq.username", rabbit::getAdminUsername)
            registry.add("spring.rabbitmq.password", rabbit::getAdminPassword)
            registry.add("spring.rabbitmq.publisher-confirm-type") { "correlated" }
        }
    }
}

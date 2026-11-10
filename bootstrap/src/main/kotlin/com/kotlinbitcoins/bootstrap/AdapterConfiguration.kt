package com.kotlinbitcoins.bootstrap

import com.fasterxml.jackson.databind.ObjectMapper
import com.kotlinbitcoins.application.notification.SendEmailNotification
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.EmailSender
import com.kotlinbitcoins.application.port.NotificationPublisher
import com.kotlinbitcoins.application.port.PasswordHasher
import com.kotlinbitcoins.application.port.QuoteCache
import com.kotlinbitcoins.application.port.QuoteProvider
import com.kotlinbitcoins.application.port.TokenIssuer
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.infrastructure.email.BrevoEmailSender
import com.kotlinbitcoins.infrastructure.email.SmtpEmailSender
import com.kotlinbitcoins.infrastructure.messaging.EmailNotificationListener
import com.kotlinbitcoins.infrastructure.messaging.OutboxNotificationPublisher
import com.kotlinbitcoins.infrastructure.messaging.OutboxRelayJob
import com.kotlinbitcoins.infrastructure.messaging.RabbitConfig
import com.kotlinbitcoins.infrastructure.messaging.SpringDataOutboxEventRepository
import com.kotlinbitcoins.infrastructure.persistence.JpaTransactionRunner
import com.kotlinbitcoins.infrastructure.persistence.account.AccountRepositoryAdapter
import com.kotlinbitcoins.infrastructure.persistence.account.SpringDataAccountRepository
import com.kotlinbitcoins.infrastructure.persistence.ledger.SpringDataTransactionRepository
import com.kotlinbitcoins.infrastructure.persistence.ledger.TransactionRepositoryAdapter
import com.kotlinbitcoins.infrastructure.quote.MercadoBitcoinQuoteProvider
import com.kotlinbitcoins.infrastructure.quote.QuoteProviderProperties
import com.kotlinbitcoins.infrastructure.quote.RedisQuoteCache
import com.kotlinbitcoins.infrastructure.security.BCryptPasswordHasher
import com.kotlinbitcoins.infrastructure.security.JwtTokenIssuer
import com.kotlinbitcoins.infrastructure.time.SystemClock
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.transaction.PlatformTransactionManager
import java.time.Duration
import java.time.ZoneId
import javax.crypto.SecretKey

/**
 * Binds each application port to its infrastructure adapter (R-02). [RabbitConfig] declares the e-mail queues; the
 * [EmailSender] depends on the profile: Brevo in `prod`, SMTP (Mailpit) everywhere else.
 */
@Configuration
@Import(RabbitConfig::class)
// Composition root: one @Bean per adapter, so the function count grows with the ports, not with complexity.
@Suppress("TooManyFunctions")
class AdapterConfiguration {
    @Bean
    fun clock(): Clock = SystemClock()

    @Bean
    fun businessZone(
        @Value("\${app.business-zone}") zone: String,
    ): ZoneId = ZoneId.of(zone)

    @Bean
    fun transactionRunner(transactionManager: PlatformTransactionManager): TransactionRunner =
        JpaTransactionRunner(transactionManager)

    @Bean
    fun accountRepository(springDataAccountRepository: SpringDataAccountRepository): AccountRepository =
        AccountRepositoryAdapter(springDataAccountRepository)

    @Bean
    fun passwordHasher(): PasswordHasher = BCryptPasswordHasher()

    /** Signs with the same key the resource server uses to validate (`jwtSecretKey` in [SecurityConfiguration]). */
    @Bean
    fun tokenIssuer(
        jwtSecretKey: SecretKey,
        clock: Clock,
        @Value("\${app.jwt.ttl}") ttl: Duration,
    ): TokenIssuer = JwtTokenIssuer(jwtSecretKey, clock, ttl)

    @Bean
    fun transactionRepository(repository: SpringDataTransactionRepository): TransactionRepository =
        TransactionRepositoryAdapter(repository)

    @Bean
    fun notificationPublisher(
        repository: SpringDataOutboxEventRepository,
        objectMapper: ObjectMapper,
    ): NotificationPublisher = OutboxNotificationPublisher(repository, objectMapper)

    @Bean
    fun quoteProviderProperties(
        @Value("\${quote.provider.url}") url: String,
        @Value("\${quote.provider.connect-timeout}") connectTimeout: Duration,
        @Value("\${quote.provider.read-timeout}") readTimeout: Duration,
    ): QuoteProviderProperties = QuoteProviderProperties(url, connectTimeout, readTimeout)

    @Bean
    fun quoteProvider(
        properties: QuoteProviderProperties,
        clock: Clock,
    ): QuoteProvider = MercadoBitcoinQuoteProvider(properties, clock)

    @Bean
    fun quoteCache(
        redis: StringRedisTemplate,
        objectMapper: ObjectMapper,
    ): QuoteCache = RedisQuoteCache(redis, objectMapper)

    @Bean
    @Profile("prod")
    fun brevoEmailSender(
        @Value("\${app.brevo.base-url}") baseUrl: String,
        @Value("\${app.brevo.api-key}") apiKey: String,
        @Value("\${app.mail.from}") from: String,
        @Value("\${app.mail.sender-name}") senderName: String,
    ): EmailSender = BrevoEmailSender(baseUrl, apiKey, from, senderName)

    @Bean
    @Profile("!prod")
    fun smtpEmailSender(
        mailSender: JavaMailSender,
        @Value("\${app.mail.from}") from: String,
    ): EmailSender = SmtpEmailSender(mailSender, from)

    @Bean
    fun outboxRelayJob(
        outbox: SpringDataOutboxEventRepository,
        rabbitTemplate: RabbitTemplate,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): OutboxRelayJob = OutboxRelayJob(outbox, rabbitTemplate, transactionRunner, clock)

    @Bean
    fun emailNotificationListener(
        sendEmailNotification: SendEmailNotification,
        outbox: SpringDataOutboxEventRepository,
        rabbitTemplate: RabbitTemplate,
        objectMapper: ObjectMapper,
        clock: Clock,
    ): EmailNotificationListener =
        EmailNotificationListener(sendEmailNotification, outbox, rabbitTemplate, objectMapper, clock)
}

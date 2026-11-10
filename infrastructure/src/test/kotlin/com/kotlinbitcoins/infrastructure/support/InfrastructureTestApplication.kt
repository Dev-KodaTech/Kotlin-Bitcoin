package com.kotlinbitcoins.infrastructure.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.NotificationPublisher
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.infrastructure.messaging.OutboxNotificationPublisher
import com.kotlinbitcoins.infrastructure.messaging.SpringDataOutboxEventRepository
import com.kotlinbitcoins.infrastructure.persistence.JpaTransactionRunner
import com.kotlinbitcoins.infrastructure.persistence.account.AccountRepositoryAdapter
import com.kotlinbitcoins.infrastructure.persistence.account.SpringDataAccountRepository
import com.kotlinbitcoins.infrastructure.persistence.ledger.SpringDataTransactionRepository
import com.kotlinbitcoins.infrastructure.persistence.ledger.TransactionRepositoryAdapter
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.PlatformTransactionManager

/** Minimal Spring context for adapter integration tests: JPA + Flyway, and the adapters under test as beans. */
@SpringBootApplication
@EnableJpaRepositories(
    basePackages = ["com.kotlinbitcoins.infrastructure.persistence", "com.kotlinbitcoins.infrastructure.messaging"],
)
@EntityScan(
    basePackages = ["com.kotlinbitcoins.infrastructure.persistence", "com.kotlinbitcoins.infrastructure.messaging"],
)
class InfrastructureTestApplication {
    @Bean
    fun transactionRunner(transactionManager: PlatformTransactionManager): TransactionRunner =
        JpaTransactionRunner(transactionManager)

    @Bean
    fun accountRepository(springDataAccountRepository: SpringDataAccountRepository): AccountRepository =
        AccountRepositoryAdapter(springDataAccountRepository)

    @Bean
    fun transactionRepository(repository: SpringDataTransactionRepository): TransactionRepository =
        TransactionRepositoryAdapter(repository)

    @Bean
    fun notificationPublisher(
        repository: SpringDataOutboxEventRepository,
        objectMapper: ObjectMapper,
    ): NotificationPublisher = OutboxNotificationPublisher(repository, objectMapper)
}

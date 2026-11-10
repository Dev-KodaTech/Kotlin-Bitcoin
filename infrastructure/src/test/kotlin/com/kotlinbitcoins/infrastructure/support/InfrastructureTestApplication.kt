package com.kotlinbitcoins.infrastructure.support

import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.infrastructure.persistence.JpaTransactionRunner
import com.kotlinbitcoins.infrastructure.persistence.account.AccountRepositoryAdapter
import com.kotlinbitcoins.infrastructure.persistence.account.SpringDataAccountRepository
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.PlatformTransactionManager

/** Minimal Spring context for adapter integration tests: JPA + Flyway, and the adapters under test as beans. */
@SpringBootApplication
@EnableJpaRepositories(basePackages = ["com.kotlinbitcoins.infrastructure.persistence"])
@EntityScan(basePackages = ["com.kotlinbitcoins.infrastructure.persistence"])
class InfrastructureTestApplication {
    @Bean
    fun transactionRunner(transactionManager: PlatformTransactionManager): TransactionRunner =
        JpaTransactionRunner(transactionManager)

    @Bean
    fun accountRepository(springDataAccountRepository: SpringDataAccountRepository): AccountRepository =
        AccountRepositoryAdapter(springDataAccountRepository)
}

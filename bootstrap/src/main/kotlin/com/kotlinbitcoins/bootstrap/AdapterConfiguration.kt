package com.kotlinbitcoins.bootstrap

import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.PasswordHasher
import com.kotlinbitcoins.application.port.TokenIssuer
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.infrastructure.persistence.JpaTransactionRunner
import com.kotlinbitcoins.infrastructure.persistence.account.AccountRepositoryAdapter
import com.kotlinbitcoins.infrastructure.persistence.account.SpringDataAccountRepository
import com.kotlinbitcoins.infrastructure.security.BCryptPasswordHasher
import com.kotlinbitcoins.infrastructure.security.JwtTokenIssuer
import com.kotlinbitcoins.infrastructure.time.SystemClock
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import java.time.Duration
import java.time.ZoneId
import javax.crypto.SecretKey

/** Binds each application port to its infrastructure adapter (R-02). */
@Configuration
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
}

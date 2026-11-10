package com.kotlinbitcoins.bootstrap

import com.kotlinbitcoins.application.account.AuthenticateAccount
import com.kotlinbitcoins.application.account.RegisterAccount
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.PasswordHasher
import com.kotlinbitcoins.application.port.TokenIssuer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers each use case as a `@Bean`, built from its ports. Use cases are plain Kotlin classes from the
 * `application` module and are never component-scanned. Each user story adds its use cases here.
 */
@Configuration
class UseCaseConfiguration {
    // US1 — registration and login

    @Bean
    fun registerAccount(
        accounts: AccountRepository,
        passwordHasher: PasswordHasher,
        clock: Clock,
    ): RegisterAccount = RegisterAccount(accounts, passwordHasher, clock)

    @Bean
    fun authenticateAccount(
        accounts: AccountRepository,
        passwordHasher: PasswordHasher,
        tokenIssuer: TokenIssuer,
    ): AuthenticateAccount = AuthenticateAccount(accounts, passwordHasher, tokenIssuer)
}

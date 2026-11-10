package com.kotlinbitcoins.bootstrap

import com.kotlinbitcoins.application.account.AuthenticateAccount
import com.kotlinbitcoins.application.account.DepositFunds
import com.kotlinbitcoins.application.account.GetBalance
import com.kotlinbitcoins.application.account.RegisterAccount
import com.kotlinbitcoins.application.investment.GetInvestmentPosition
import com.kotlinbitcoins.application.investment.PurchaseBitcoin
import com.kotlinbitcoins.application.investment.RedeemInvestments
import com.kotlinbitcoins.application.ledger.GetDailyVolume
import com.kotlinbitcoins.application.ledger.GetStatement
import com.kotlinbitcoins.application.notification.SendEmailNotification
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.EmailSender
import com.kotlinbitcoins.application.port.InvestmentRepository
import com.kotlinbitcoins.application.port.NotificationPublisher
import com.kotlinbitcoins.application.port.PasswordHasher
import com.kotlinbitcoins.application.port.QuoteCache
import com.kotlinbitcoins.application.port.QuoteProvider
import com.kotlinbitcoins.application.port.TokenIssuer
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.application.quote.GetCurrentQuote
import com.kotlinbitcoins.application.quote.QuoteService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.ZoneId

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

    // US2 — deposit and e-mail notification

    @Bean
    fun depositFunds(
        accounts: AccountRepository,
        transactions: TransactionRepository,
        notifications: NotificationPublisher,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): DepositFunds = DepositFunds(accounts, transactions, notifications, transactionRunner, clock)

    @Bean
    fun sendEmailNotification(emailSender: EmailSender): SendEmailNotification = SendEmailNotification(emailSender)

    // US3 — balance

    @Bean
    fun getBalance(accounts: AccountRepository): GetBalance = GetBalance(accounts)

    // US4 — current quote

    @Bean
    fun quoteService(
        provider: QuoteProvider,
        cache: QuoteCache,
        clock: Clock,
    ): QuoteService = QuoteService(provider, cache, clock)

    @Bean
    fun getCurrentQuote(quoteService: QuoteService): GetCurrentQuote = GetCurrentQuote(quoteService)

    // US5 — purchase of bitcoin

    @Bean
    @Suppress("LongParameterList")
    fun purchaseBitcoin(
        accounts: AccountRepository,
        investments: InvestmentRepository,
        transactions: TransactionRepository,
        notifications: NotificationPublisher,
        quoteService: QuoteService,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): PurchaseBitcoin =
        PurchaseBitcoin(accounts, investments, transactions, notifications, quoteService, transactionRunner, clock)

    // US6 — investment position

    @Bean
    fun getInvestmentPosition(
        investments: InvestmentRepository,
        quoteService: QuoteService,
    ): GetInvestmentPosition = GetInvestmentPosition(investments, quoteService)

    // US7 — redemption of bitcoin

    @Bean
    @Suppress("LongParameterList")
    fun redeemInvestments(
        accounts: AccountRepository,
        investments: InvestmentRepository,
        transactions: TransactionRepository,
        notifications: NotificationPublisher,
        quoteService: QuoteService,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): RedeemInvestments =
        RedeemInvestments(accounts, investments, transactions, notifications, quoteService, transactionRunner, clock)

    // US8 — statement

    @Bean
    fun getStatement(
        transactions: TransactionRepository,
        clock: Clock,
        businessZone: ZoneId,
    ): GetStatement = GetStatement(transactions, clock, businessZone)

    // US9 — daily volume

    @Bean
    fun getDailyVolume(
        transactions: TransactionRepository,
        clock: Clock,
        businessZone: ZoneId,
    ): GetDailyVolume = GetDailyVolume(transactions, clock, businessZone)
}

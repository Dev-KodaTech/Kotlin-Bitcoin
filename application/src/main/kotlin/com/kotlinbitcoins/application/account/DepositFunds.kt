package com.kotlinbitcoins.application.account

import com.kotlinbitcoins.application.account.DepositError.AccountNotFound
import com.kotlinbitcoins.application.account.DepositError.ServiceUnavailable
import com.kotlinbitcoins.application.notification.NotificationEvent
import com.kotlinbitcoins.application.notification.NotificationType
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.NotificationPublisher
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.ledger.Transaction
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import com.kotlinbitcoins.domain.shared.TransactionId
import com.kotlinbitcoins.domain.shared.flatMap
import com.kotlinbitcoins.domain.shared.map
import com.kotlinbitcoins.domain.shared.mapError
import java.time.Instant
import java.util.UUID

data class DepositCommand(
    val accountId: AccountId,
    val amount: PositiveMoney,
)

data class DepositReceipt(
    val transactionId: TransactionId,
    val amount: Money,
    val balance: Money,
    val occurredAt: Instant,
)

/**
 * Credits a deposit to the account at once (US2, FR-008, FR-009). The account row is locked first (R-05), then
 * balance, statement line and notification event are written in a single transaction: either all of them exist or
 * none does (FR-034). The e-mail itself is sent later, from the outbox, so its failure never undoes the deposit
 * (FR-031).
 */
class DepositFunds(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val notifications: NotificationPublisher,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(command: DepositCommand): Outcome<DepositError, DepositReceipt> =
        transactionRunner.run<DepositError, DepositReceipt>({ ServiceUnavailable }) { deposit(command) }

    private fun deposit(command: DepositCommand): Outcome<DepositError, DepositReceipt> =
        lock(command.accountId).flatMap { account ->
            val now = clock.now()
            val credited = account.deposit(command.amount)
            val line = Transaction.deposit(account.id, command.amount, now)
            save(credited)
                .flatMap { record(line) }
                .flatMap { publish(depositConfirmed(credited, command.amount, now)) }
                .map { DepositReceipt(line.id, command.amount.money, credited.balance, now) }
        }

    private fun lock(accountId: AccountId): Outcome<DepositError, Account> =
        accounts
            .findByIdForUpdate(accountId)
            .mapError<_, _, DepositError> { ServiceUnavailable }
            .flatMap { account -> if (account == null) Outcome.Failure(AccountNotFound) else Outcome.Success(account) }

    private fun save(account: Account): Outcome<DepositError, Unit> =
        accounts.save(account).mapError<_, _, DepositError> { ServiceUnavailable }.map { }

    private fun record(line: Transaction): Outcome<DepositError, Unit> =
        transactions.saveAll(listOf(line)).mapError { ServiceUnavailable }

    private fun publish(event: NotificationEvent): Outcome<DepositError, Unit> =
        notifications.publish(event).mapError { ServiceUnavailable }

    private fun depositConfirmed(
        account: Account,
        amount: PositiveMoney,
        now: Instant,
    ) = NotificationEvent(
        id = UUID.randomUUID(),
        type = NotificationType.DEPOSIT_CONFIRMED,
        accountId = account.id,
        recipientEmail = account.email,
        recipientName = account.name,
        amount = amount.money,
        createdAt = now,
    )
}

package com.kotlinbitcoins.application.account

import com.kotlinbitcoins.application.notification.NotificationEvent
import com.kotlinbitcoins.application.notification.NotificationType
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.NotificationPublisher
import com.kotlinbitcoins.application.port.SaveAccountError
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.ledger.Transaction
import com.kotlinbitcoins.domain.ledger.TransactionType
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class DepositFundsTest {
    private val now = Instant.parse("2026-11-09T11:10:00Z")
    private val accountId = AccountId.random()
    private val accounts = mockk<AccountRepository>()
    private val transactions = mockk<TransactionRepository>()
    private val notifications = mockk<NotificationPublisher>()
    private val clock = Clock { now }
    private val runner = RecordingTransactionRunner()

    private val depositFunds = DepositFunds(accounts, transactions, notifications, runner, clock)

    @Test
    fun `credits the balance, records the statement line and publishes the notification inside one transaction`() {
        val saved = slot<Account>()
        val lines = slot<List<Transaction>>()
        val event = slot<NotificationEvent>()
        every { accounts.findByIdForUpdate(accountId) } answers { insideTransaction(Outcome.Success(account("50.00"))) }
        every { accounts.save(capture(saved)) } answers { insideTransaction(Outcome.Success(saved.captured)) }
        every { transactions.saveAll(capture(lines)) } answers { insideTransaction(Outcome.Success(Unit)) }
        every { notifications.publish(capture(event)) } answers { insideTransaction(Outcome.Success(Unit)) }

        val result = depositFunds.execute(DepositCommand(accountId, positive("100.00")))

        val receipt = (result as Outcome.Success).value
        assertThat(receipt.amount).isEqualTo(money("100.00"))
        assertThat(receipt.balance).isEqualTo(money("150.00"))
        assertThat(receipt.occurredAt).isEqualTo(now)
        assertThat(saved.captured.balance).isEqualTo(money("150.00"))
        assertThat(lines.captured).hasSize(1)
        val line = lines.captured.single()
        assertThat(line.type).isEqualTo(TransactionType.DEPOSIT)
        assertThat(line.amount).isEqualTo(money("100.00"))
        assertThat(line.occurredAt).isEqualTo(now)
        assertThat(line.id).isEqualTo(receipt.transactionId)
        assertThat(event.captured.type).isEqualTo(NotificationType.DEPOSIT_CONFIRMED)
        assertThat(event.captured.amount).isEqualTo(money("100.00"))
        assertThat(event.captured.recipientEmail.value).isEqualTo("ana@example.com")
        assertThat(event.captured.recipientName).isEqualTo("Ana Souza")
        assertThat(event.captured.accountId).isEqualTo(accountId)
        assertThat(event.captured.createdAt).isEqualTo(now)
        assertThat(runner.runs).isEqualTo(1)
    }

    @Test
    fun `unknown account is AccountNotFound and nothing is written`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(null)

        val result = depositFunds.execute(DepositCommand(accountId, positive("100.00")))

        assertThat(result).isEqualTo(Outcome.Failure(DepositError.AccountNotFound))
        verify(exactly = 0) { accounts.save(any()) }
        verify(exactly = 0) { transactions.saveAll(any()) }
        verify(exactly = 0) { notifications.publish(any()) }
    }

    @Test
    fun `failure loading the account is ServiceUnavailable with no partial write or event`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = depositFunds.execute(DepositCommand(accountId, positive("100.00")))

        assertThat(result).isEqualTo(Outcome.Failure(DepositError.ServiceUnavailable))
        verify(exactly = 0) { accounts.save(any()) }
        verify(exactly = 0) { transactions.saveAll(any()) }
        verify(exactly = 0) { notifications.publish(any()) }
    }

    @Test
    fun `failure saving the account is ServiceUnavailable with no statement line or event`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(account("0.00"))
        every { accounts.save(any()) } returns
            Outcome.Failure(SaveAccountError.Infrastructure(InfrastructureFailure("db down")))

        val result = depositFunds.execute(DepositCommand(accountId, positive("100.00")))

        assertThat(result).isEqualTo(Outcome.Failure(DepositError.ServiceUnavailable))
        verify(exactly = 0) { transactions.saveAll(any()) }
        verify(exactly = 0) { notifications.publish(any()) }
    }

    @Test
    fun `failure saving the statement line is ServiceUnavailable and publishes no event`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(account("0.00"))
        every { accounts.save(any()) } answers { Outcome.Success(firstArg()) }
        every { transactions.saveAll(any()) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = depositFunds.execute(DepositCommand(accountId, positive("100.00")))

        assertThat(result).isEqualTo(Outcome.Failure(DepositError.ServiceUnavailable))
        verify(exactly = 0) { notifications.publish(any()) }
    }

    @Test
    fun `failure publishing the notification is ServiceUnavailable so the deposit is rolled back`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(account("0.00"))
        every { accounts.save(any()) } answers { Outcome.Success(firstArg()) }
        every { transactions.saveAll(any()) } returns Outcome.Success(Unit)
        every { notifications.publish(any()) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = depositFunds.execute(DepositCommand(accountId, positive("100.00")))

        assertThat(result).isEqualTo(Outcome.Failure(DepositError.ServiceUnavailable))
    }

    @Test
    fun `commit failure reported by the runner is ServiceUnavailable`() {
        runner.failCommit = true
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(account("0.00"))
        every { accounts.save(any()) } answers { Outcome.Success(firstArg()) }
        every { transactions.saveAll(any()) } returns Outcome.Success(Unit)
        every { notifications.publish(any()) } returns Outcome.Success(Unit)

        val result = depositFunds.execute(DepositCommand(accountId, positive("100.00")))

        assertThat(result).isEqualTo(Outcome.Failure(DepositError.ServiceUnavailable))
    }

    private fun <T> insideTransaction(value: T): T {
        assertThat(runner.isInsideTransaction).describedAs("port called inside TransactionRunner.run").isTrue()
        return value
    }

    private fun account(balance: String) =
        Account(
            id = accountId,
            name = "Ana Souza",
            email = (Email.of("ana@example.com") as Outcome.Success).value,
            passwordHash = "hash",
            balance = money(balance),
            createdAt = now,
        )

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    private fun positive(value: String): PositiveMoney = (PositiveMoney.of(BigDecimal(value)) as Outcome.Success).value
}

/** Runs the block like a real runner: failure values roll back, and a commit failure becomes a value. */
class RecordingTransactionRunner : TransactionRunner {
    var runs = 0
        private set
    var isInsideTransaction = false
        private set
    var failCommit = false

    override fun <E, T> run(
        onInfrastructureFailure: (InfrastructureFailure) -> E,
        block: () -> Outcome<E, T>,
    ): Outcome<E, T> {
        runs++
        isInsideTransaction = true
        val result =
            try {
                block()
            } finally {
                isInsideTransaction = false
            }
        return if (failCommit && result is Outcome.Success) {
            Outcome.Failure(onInfrastructureFailure(InfrastructureFailure("commit failed")))
        } else {
            result
        }
    }
}

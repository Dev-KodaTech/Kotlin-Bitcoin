package com.kotlinbitcoins.application.investment

import com.kotlinbitcoins.application.account.RecordingTransactionRunner
import com.kotlinbitcoins.application.notification.NotificationEvent
import com.kotlinbitcoins.application.notification.NotificationType
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.InvestmentRepository
import com.kotlinbitcoins.application.port.NotificationPublisher
import com.kotlinbitcoins.application.port.SaveAccountError
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.application.quote.QuoteError
import com.kotlinbitcoins.application.quote.QuoteService
import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.investment.Investment
import com.kotlinbitcoins.domain.investment.InvestmentOrigin
import com.kotlinbitcoins.domain.investment.InvestmentStatus
import com.kotlinbitcoins.domain.ledger.Transaction
import com.kotlinbitcoins.domain.ledger.TransactionType
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import com.kotlinbitcoins.domain.shared.QuotePrice
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class PurchaseBitcoinTest {
    private val now = Instant.parse("2026-11-09T11:10:00Z")
    private val quotedAt = Instant.parse("2026-11-09T11:09:55Z")
    private val accountId = AccountId.random()
    private val accounts = mockk<AccountRepository>()
    private val investments = mockk<InvestmentRepository>()
    private val transactions = mockk<TransactionRepository>()
    private val notifications = mockk<NotificationPublisher>()
    private val quoteService = mockk<QuoteService>()
    private val clock = Clock { now }
    private val runner = RecordingTransactionRunner()

    private val purchaseBitcoin =
        PurchaseBitcoin(accounts, investments, transactions, notifications, quoteService, runner, clock)

    @Test
    fun `converts at the sell price, debits and records investment, line and event in one transaction`() {
        val savedAccount = slot<Account>()
        val savedInvestment = slot<Investment>()
        val lines = slot<List<Transaction>>()
        val event = slot<NotificationEvent>()
        every { accounts.findByIdForUpdate(accountId) } answers { inTx(Outcome.Success(account("1000.00"))) }
        every { quoteService.currentQuote() } answers
            { inTx(Outcome.Success(quote(buy = "399000.00", sell = "400000.00"))) }
        every { investments.nextFifoSequence() } answers { inTx(Outcome.Success(42L)) }
        every { accounts.save(capture(savedAccount)) } answers { inTx(Outcome.Success(savedAccount.captured)) }
        every { investments.save(capture(savedInvestment)) } answers { inTx(Outcome.Success(savedInvestment.captured)) }
        every { transactions.saveAll(capture(lines)) } answers { inTx(Outcome.Success(Unit)) }
        every { notifications.publish(capture(event)) } answers { inTx(Outcome.Success(Unit)) }

        val result = purchaseBitcoin.execute(PurchaseCommand(accountId, positive("400.00")))

        val receipt = (result as Outcome.Success).value
        assertThat(receipt.amount).isEqualTo(money("400.00"))
        assertThat(receipt.btcAmount).isEqualTo(BtcAmount(100_000))
        assertThat(receipt.quote).isEqualTo(price("400000.00"))
        assertThat(receipt.purchasedAt).isEqualTo(now)
        assertThat(receipt.balance).isEqualTo(money("600.00"))
        assertThat(savedAccount.captured.balance).isEqualTo(money("600.00"))

        val investment = savedInvestment.captured
        assertThat(investment.id).isEqualTo(receipt.investmentId)
        assertThat(investment.accountId).isEqualTo(accountId)
        assertThat(investment.purchasedAt).isEqualTo(now)
        assertThat(investment.fifoSequence).isEqualTo(42L)
        assertThat(investment.investedAmount).isEqualTo(money("400.00"))
        assertThat(investment.purchaseQuote).isEqualTo(price("400000.00"))
        assertThat(investment.btcAmount).isEqualTo(BtcAmount(100_000))
        assertThat(investment.status).isEqualTo(InvestmentStatus.ACTIVE)
        assertThat(investment.origin).isEqualTo(InvestmentOrigin.PURCHASE)

        val line = lines.captured.single()
        assertThat(line.id).isEqualTo(receipt.transactionId)
        assertThat(line.type).isEqualTo(TransactionType.PURCHASE)
        assertThat(line.amount).isEqualTo(money("400.00"))
        assertThat(line.btcAmount).isEqualTo(BtcAmount(100_000))
        assertThat(line.quote).isEqualTo(price("400000.00"))
        assertThat(line.investmentId).isEqualTo(investment.id)
        assertThat(line.occurredAt).isEqualTo(now)

        assertThat(event.captured.type).isEqualTo(NotificationType.PURCHASE_CONFIRMED)
        assertThat(event.captured.amount).isEqualTo(money("400.00"))
        assertThat(event.captured.btcAmount).isEqualTo(BtcAmount(100_000))
        assertThat(event.captured.recipientEmail.value).isEqualTo("ana@example.com")
        assertThat(event.captured.recipientName).isEqualTo("Ana Souza")
        assertThat(event.captured.accountId).isEqualTo(accountId)
        assertThat(event.captured.createdAt).isEqualTo(now)

        assertThat(runner.runs).isEqualTo(1)
        verify(exactly = 1) { quoteService.currentQuote() }
    }

    @Test
    fun `insufficient balance writes nothing and publishes no event`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(account("100.00"))
        every { quoteService.currentQuote() } returns Outcome.Success(quote())

        val result = purchaseBitcoin.execute(PurchaseCommand(accountId, positive("100.01")))

        assertThat(result).isEqualTo(Outcome.Failure(PurchaseError.InsufficientBalance))
        verifyNothingWritten()
    }

    @Test
    fun `quote unavailable writes nothing`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(account("1000.00"))
        every { quoteService.currentQuote() } returns Outcome.Failure(QuoteError.QuoteUnavailable)

        val result = purchaseBitcoin.execute(PurchaseCommand(accountId, positive("10.00")))

        assertThat(result).isEqualTo(Outcome.Failure(PurchaseError.QuoteUnavailable))
        verifyNothingWritten()
    }

    @Test
    fun `an amount worth less than one satoshi is an invalid amount and writes nothing`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(account("1000.00"))
        every { quoteService.currentQuote() } returns Outcome.Success(quote(sell = "2000000.00"))

        val result = purchaseBitcoin.execute(PurchaseCommand(accountId, positive("0.01")))

        assertThat(result).isEqualTo(Outcome.Failure(PurchaseError.InvalidAmount))
        verifyNothingWritten()
    }

    @Test
    fun `unknown account is AccountNotFound and the quote is not even requested`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(null)

        val result = purchaseBitcoin.execute(PurchaseCommand(accountId, positive("10.00")))

        assertThat(result).isEqualTo(Outcome.Failure(PurchaseError.AccountNotFound))
        verify(exactly = 0) { quoteService.currentQuote() }
        verifyNothingWritten()
    }

    @Test
    fun `failure loading the account is ServiceUnavailable with no partial write or event`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = purchaseBitcoin.execute(PurchaseCommand(accountId, positive("10.00")))

        assertThat(result).isEqualTo(Outcome.Failure(PurchaseError.ServiceUnavailable))
        verifyNothingWritten()
    }

    @Test
    fun `failure reserving the FIFO sequence is ServiceUnavailable with no write`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(account("1000.00"))
        every { quoteService.currentQuote() } returns Outcome.Success(quote())
        every { investments.nextFifoSequence() } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = purchaseBitcoin.execute(PurchaseCommand(accountId, positive("10.00")))

        assertThat(result).isEqualTo(Outcome.Failure(PurchaseError.ServiceUnavailable))
        verifyNothingWritten()
    }

    @Test
    fun `failure saving the account is ServiceUnavailable with no investment, line or event`() {
        happyReads()
        every { accounts.save(any()) } returns
            Outcome.Failure(SaveAccountError.Infrastructure(InfrastructureFailure("db down")))

        val result = purchaseBitcoin.execute(PurchaseCommand(accountId, positive("10.00")))

        assertThat(result).isEqualTo(Outcome.Failure(PurchaseError.ServiceUnavailable))
        verify(exactly = 0) { investments.save(any()) }
        verify(exactly = 0) { transactions.saveAll(any()) }
        verify(exactly = 0) { notifications.publish(any()) }
    }

    @Test
    fun `failure saving the investment is ServiceUnavailable with no line or event`() {
        happyReads()
        every { accounts.save(any()) } answers { Outcome.Success(firstArg()) }
        every { investments.save(any()) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = purchaseBitcoin.execute(PurchaseCommand(accountId, positive("10.00")))

        assertThat(result).isEqualTo(Outcome.Failure(PurchaseError.ServiceUnavailable))
        verify(exactly = 0) { transactions.saveAll(any()) }
        verify(exactly = 0) { notifications.publish(any()) }
    }

    @Test
    fun `failure saving the statement line is ServiceUnavailable with no event`() {
        happyReads()
        every { accounts.save(any()) } answers { Outcome.Success(firstArg()) }
        every { investments.save(any()) } answers { Outcome.Success(firstArg()) }
        every { transactions.saveAll(any()) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = purchaseBitcoin.execute(PurchaseCommand(accountId, positive("10.00")))

        assertThat(result).isEqualTo(Outcome.Failure(PurchaseError.ServiceUnavailable))
        verify(exactly = 0) { notifications.publish(any()) }
    }

    @Test
    fun `failure publishing the event is ServiceUnavailable so the purchase is rolled back`() {
        happyReads()
        every { accounts.save(any()) } answers { Outcome.Success(firstArg()) }
        every { investments.save(any()) } answers { Outcome.Success(firstArg()) }
        every { transactions.saveAll(any()) } returns Outcome.Success(Unit)
        every { notifications.publish(any()) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = purchaseBitcoin.execute(PurchaseCommand(accountId, positive("10.00")))

        assertThat(result).isEqualTo(Outcome.Failure(PurchaseError.ServiceUnavailable))
    }

    @Test
    fun `commit failure reported by the runner is ServiceUnavailable`() {
        runner.failCommit = true
        happyReads()
        every { accounts.save(any()) } answers { Outcome.Success(firstArg()) }
        every { investments.save(any()) } answers { Outcome.Success(firstArg()) }
        every { transactions.saveAll(any()) } returns Outcome.Success(Unit)
        every { notifications.publish(any()) } returns Outcome.Success(Unit)

        val result = purchaseBitcoin.execute(PurchaseCommand(accountId, positive("10.00")))

        assertThat(result).isEqualTo(Outcome.Failure(PurchaseError.ServiceUnavailable))
    }

    private fun happyReads() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(account("1000.00"))
        every { quoteService.currentQuote() } returns Outcome.Success(quote())
        every { investments.nextFifoSequence() } returns Outcome.Success(1L)
    }

    private fun verifyNothingWritten() {
        verify(exactly = 0) { accounts.save(any()) }
        verify(exactly = 0) { investments.save(any()) }
        verify(exactly = 0) { transactions.saveAll(any()) }
        verify(exactly = 0) { notifications.publish(any()) }
    }

    private fun <T> inTx(value: T): T {
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

    private fun quote(
        buy: String = "400000.00",
        sell: String = "400000.00",
    ) = Quote(price(buy), price(sell), quotedAt)

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    private fun positive(value: String): PositiveMoney = (PositiveMoney.of(BigDecimal(value)) as Outcome.Success).value

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}

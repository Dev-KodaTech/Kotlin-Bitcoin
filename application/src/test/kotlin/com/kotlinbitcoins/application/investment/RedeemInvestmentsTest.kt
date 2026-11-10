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

class RedeemInvestmentsTest {
    private val now = Instant.parse("2026-11-09T11:10:00Z")
    private val quotedAt = Instant.parse("2026-11-09T11:09:55Z")
    private val boughtA = Instant.parse("2026-11-01T10:00:00Z")
    private val boughtB = Instant.parse("2026-11-02T10:00:00Z")
    private val accountId = AccountId.random()
    private val accounts = mockk<AccountRepository>()
    private val investments = mockk<InvestmentRepository>()
    private val transactions = mockk<TransactionRepository>()
    private val notifications = mockk<NotificationPublisher>()
    private val quoteService = mockk<QuoteService>()
    private val clock = Clock { now }
    private val runner = RecordingTransactionRunner()

    // A holds 0.001 BTC and B 0.0005 BTC, both bought at 400000.00; the buy price is now 200000.00,
    // so A is worth 200.00 and B 100.00 (quickstart steps 12–14).
    private val a = investment(boughtA, 1, 100_000)
    private val b = investment(boughtB, 2, 50_000)

    private val redeemInvestments =
        RedeemInvestments(accounts, investments, transactions, notifications, quoteService, runner, clock)

    /** What a redemption of 250.00 wrote, captured from the ports. */
    private class Written(
        val receipt: RedemptionReceipt,
        val closed: List<Investment>,
        val saved: List<Investment>,
        val savedAccount: List<Account>,
        val lines: List<Transaction>,
        val events: List<NotificationEvent>,
    )

    private fun redeem250(): Written {
        val closed = mutableListOf<Investment>()
        val saved = mutableListOf<Investment>()
        val savedAccount = mutableListOf<Account>()
        val savedLines = slot<List<Transaction>>()
        val events = mutableListOf<NotificationEvent>()
        every { accounts.findByIdForUpdate(accountId) } answers { inTx(Outcome.Success(account("10.00"))) }
        every { quoteService.currentQuote() } answers { inTx(Outcome.Success(quote(buy = "200000.00"))) }
        every { investments.findActiveByAccountInFifoOrder(accountId) } answers { inTx(Outcome.Success(listOf(a, b))) }
        every { investments.close(capture(closed)) } answers { inTx(Outcome.Success(closed.last())) }
        every { investments.save(capture(saved)) } answers { inTx(Outcome.Success(saved.last())) }
        every { accounts.save(capture(savedAccount)) } answers { inTx(Outcome.Success(savedAccount.last())) }
        every { transactions.saveAll(capture(savedLines)) } answers { inTx(Outcome.Success(Unit)) }
        every { notifications.publish(capture(events)) } answers { inTx(Outcome.Success(Unit)) }

        val result = redeemInvestments.execute(RedeemCommand(accountId, positive("250.00")))

        return Written(
            (result as Outcome.Success).value,
            closed,
            saved,
            savedAccount,
            savedLines.captured,
            events,
        )
    }

    @Test
    fun `reports the liquidations in FIFO order, the reinvestment, the exact amount and the new balance`() {
        val written = redeem250()
        val receipt = written.receipt

        assertThat(receipt.amount).isEqualTo(money("250.00"))
        assertThat(receipt.btcSold).isEqualTo(BtcAmount(125_000))
        assertThat(receipt.quote).isEqualTo(price("200000.00"))
        assertThat(receipt.balance).isEqualTo(money("260.00"))
        assertThat(receipt.occurredAt).isEqualTo(now)
        assertThat(receipt.liquidations).containsExactly(
            LiquidationReceipt(a.id, money("200.00"), BtcAmount(100_000), isPartial = false),
            LiquidationReceipt(b.id, money("50.00"), BtcAmount(25_000), isPartial = true),
        )
        val reinvestment = written.saved.single()
        assertThat(receipt.reinvestment)
            .isEqualTo(
                ReinvestmentReceipt(reinvestment.id, BtcAmount(25_000), money("100.00"), price("400000.00"), boughtB),
            )
    }

    @Test
    fun `closes the liquidated investments, reinvests the residue of B and credits the exact amount`() {
        val written = redeem250()

        assertThat(written.closed.map { it.id }).containsExactly(a.id, b.id)
        assertThat(written.closed).allSatisfy {
            assertThat(it.status).isEqualTo(InvestmentStatus.CLOSED)
            assertThat(it.closedAt).isEqualTo(now)
        }
        val reinvestment = written.saved.single()
        assertThat(reinvestment.origin).isEqualTo(InvestmentOrigin.REINVESTMENT)
        assertThat(reinvestment.originInvestmentId).isEqualTo(b.id)
        assertThat(reinvestment.btcAmount).isEqualTo(BtcAmount(25_000))
        assertThat(reinvestment.purchaseQuote).isEqualTo(price("400000.00"))
        assertThat(reinvestment.investedAmount).isEqualTo(money("100.00"))
        assertThat(reinvestment.purchasedAt).isEqualTo(boughtB)
        assertThat(reinvestment.fifoSequence).isEqualTo(2)
        assertThat(written.savedAccount.single().balance).isEqualTo(money("260.00"))
        assertThat(runner.runs).isEqualTo(1)
        verify(exactly = 1) { quoteService.currentQuote() }
    }

    @Test
    fun `writes one line per liquidation and one for the reinvestment, all with the same request id`() {
        val written = redeem250()

        val lines = written.lines
        assertThat(lines.map { it.type })
            .containsExactly(TransactionType.REDEMPTION, TransactionType.REDEMPTION, TransactionType.REINVESTMENT)
        assertThat(lines.map { it.requestId }.distinct()).containsExactly(written.receipt.requestId)
        assertThat(lines.map { it.occurredAt }.distinct()).containsExactly(now)
        val (redemptionA, redemptionB, reinvested) = lines
        assertThat(redemptionA.amount).isEqualTo(money("200.00"))
        assertThat(redemptionA.btcAmount).isEqualTo(BtcAmount(100_000))
        assertThat(redemptionA.quote).isEqualTo(price("200000.00"))
        assertThat(redemptionA.investmentId).isEqualTo(a.id)
        assertThat(redemptionB.amount).isEqualTo(money("50.00"))
        assertThat(redemptionB.btcAmount).isEqualTo(BtcAmount(25_000))
        assertThat(redemptionB.quote).isEqualTo(price("200000.00"))
        assertThat(redemptionB.investmentId).isEqualTo(b.id)
        assertThat(reinvested.amount).isEqualTo(money("100.00"))
        assertThat(reinvested.btcAmount).isEqualTo(BtcAmount(25_000))
        assertThat(reinvested.quote).isEqualTo(price("400000.00"))
        assertThat(reinvested.investmentId).isEqualTo(written.saved.single().id)
    }

    @Test
    fun `publishes REDEMPTION_CONFIRMED with the amount redeemed and the total bitcoin sold`() {
        val event = redeem250().events.single()

        assertThat(event.type).isEqualTo(NotificationType.REDEMPTION_CONFIRMED)
        assertThat(event.amount).isEqualTo(money("250.00"))
        assertThat(event.btcAmount).isEqualTo(BtcAmount(125_000))
        assertThat(event.recipientEmail.value).isEqualTo("ana@example.com")
        assertThat(event.recipientName).isEqualTo("Ana Souza")
        assertThat(event.accountId).isEqualTo(accountId)
        assertThat(event.createdAt).isEqualTo(now)
    }

    @Test
    fun `a request that ends at the end of an investment creates no reinvestment`() {
        happyReads()
        savesSucceed()

        val result = redeemInvestments.execute(RedeemCommand(accountId, positive("300.00")))

        val receipt = (result as Outcome.Success).value
        assertThat(receipt.reinvestment).isNull()
        assertThat(receipt.btcSold).isEqualTo(BtcAmount(150_000))
        assertThat(receipt.liquidations.map { it.isPartial }).containsExactly(false, false)
        verify(exactly = 2) { investments.close(any()) }
        verify(exactly = 0) { investments.save(any()) }
        verify { transactions.saveAll(match { lines -> lines.all { it.type == TransactionType.REDEMPTION } }) }
    }

    @Test
    fun `a request above the current value is RedemptionExceedsAvailable and writes nothing`() {
        happyReads()

        val result = redeemInvestments.execute(RedeemCommand(accountId, positive("300.01")))

        assertThat(result).isEqualTo(Outcome.Failure(RedemptionError.RedemptionExceedsAvailable))
        verifyNothingWritten()
    }

    @Test
    fun `an account without investments is RedemptionExceedsAvailable and writes nothing`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(account("10.00"))
        every { quoteService.currentQuote() } returns Outcome.Success(quote(buy = "200000.00"))
        every { investments.findActiveByAccountInFifoOrder(accountId) } returns Outcome.Success(emptyList())

        val result = redeemInvestments.execute(RedeemCommand(accountId, positive("0.01")))

        assertThat(result).isEqualTo(Outcome.Failure(RedemptionError.RedemptionExceedsAvailable))
        verifyNothingWritten()
    }

    @Test
    fun `quote unavailable writes nothing`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(account("10.00"))
        every { quoteService.currentQuote() } returns Outcome.Failure(QuoteError.QuoteUnavailable)

        val result = redeemInvestments.execute(RedeemCommand(accountId, positive("10.00")))

        assertThat(result).isEqualTo(Outcome.Failure(RedemptionError.QuoteUnavailable))
        verifyNothingWritten()
    }

    @Test
    fun `unknown account is AccountNotFound and the quote is not even requested`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(null)

        val result = redeemInvestments.execute(RedeemCommand(accountId, positive("10.00")))

        assertThat(result).isEqualTo(Outcome.Failure(RedemptionError.AccountNotFound))
        verify(exactly = 0) { quoteService.currentQuote() }
        verifyNothingWritten()
    }

    @Test
    fun `failure loading the account is ServiceUnavailable with no write`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = redeemInvestments.execute(RedeemCommand(accountId, positive("10.00")))

        assertThat(result).isEqualTo(Outcome.Failure(RedemptionError.ServiceUnavailable))
        verifyNothingWritten()
    }

    @Test
    fun `failure loading the active investments is ServiceUnavailable with no write`() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(account("10.00"))
        every { quoteService.currentQuote() } returns Outcome.Success(quote(buy = "200000.00"))
        every { investments.findActiveByAccountInFifoOrder(accountId) } returns
            Outcome.Failure(InfrastructureFailure("db down"))

        val result = redeemInvestments.execute(RedeemCommand(accountId, positive("10.00")))

        assertThat(result).isEqualTo(Outcome.Failure(RedemptionError.ServiceUnavailable))
        verifyNothingWritten()
    }

    @Test
    fun `failure closing an investment is ServiceUnavailable with no credit, line or event`() {
        happyReads()
        every { investments.close(any()) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = redeemInvestments.execute(RedeemCommand(accountId, positive("250.00")))

        assertThat(result).isEqualTo(Outcome.Failure(RedemptionError.ServiceUnavailable))
        verify(exactly = 0) { investments.save(any()) }
        verify(exactly = 0) { accounts.save(any()) }
        verify(exactly = 0) { transactions.saveAll(any()) }
        verify(exactly = 0) { notifications.publish(any()) }
    }

    @Test
    fun `failure saving the reinvestment is ServiceUnavailable with no credit, line or event`() {
        happyReads()
        every { investments.close(any()) } answers { Outcome.Success(firstArg()) }
        every { investments.save(any()) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = redeemInvestments.execute(RedeemCommand(accountId, positive("250.00")))

        assertThat(result).isEqualTo(Outcome.Failure(RedemptionError.ServiceUnavailable))
        verify(exactly = 0) { accounts.save(any()) }
        verify(exactly = 0) { transactions.saveAll(any()) }
        verify(exactly = 0) { notifications.publish(any()) }
    }

    @Test
    fun `failure saving the account is ServiceUnavailable with no line or event`() {
        happyReads()
        every { investments.close(any()) } answers { Outcome.Success(firstArg()) }
        every { investments.save(any()) } answers { Outcome.Success(firstArg()) }
        every { accounts.save(any()) } returns
            Outcome.Failure(SaveAccountError.Infrastructure(InfrastructureFailure("db down")))

        val result = redeemInvestments.execute(RedeemCommand(accountId, positive("250.00")))

        assertThat(result).isEqualTo(Outcome.Failure(RedemptionError.ServiceUnavailable))
        verify(exactly = 0) { transactions.saveAll(any()) }
        verify(exactly = 0) { notifications.publish(any()) }
    }

    @Test
    fun `failure saving the statement lines is ServiceUnavailable with no event`() {
        happyReads()
        every { investments.close(any()) } answers { Outcome.Success(firstArg()) }
        every { investments.save(any()) } answers { Outcome.Success(firstArg()) }
        every { accounts.save(any()) } answers { Outcome.Success(firstArg()) }
        every { transactions.saveAll(any()) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = redeemInvestments.execute(RedeemCommand(accountId, positive("250.00")))

        assertThat(result).isEqualTo(Outcome.Failure(RedemptionError.ServiceUnavailable))
        verify(exactly = 0) { notifications.publish(any()) }
    }

    @Test
    fun `failure publishing the event is ServiceUnavailable so the redemption is rolled back`() {
        happyReads()
        savesSucceed()
        every { notifications.publish(any()) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = redeemInvestments.execute(RedeemCommand(accountId, positive("250.00")))

        assertThat(result).isEqualTo(Outcome.Failure(RedemptionError.ServiceUnavailable))
    }

    @Test
    fun `commit failure reported by the runner is ServiceUnavailable`() {
        runner.failCommit = true
        happyReads()
        savesSucceed()

        val result = redeemInvestments.execute(RedeemCommand(accountId, positive("250.00")))

        assertThat(result).isEqualTo(Outcome.Failure(RedemptionError.ServiceUnavailable))
    }

    private fun happyReads() {
        every { accounts.findByIdForUpdate(accountId) } returns Outcome.Success(account("10.00"))
        every { quoteService.currentQuote() } returns Outcome.Success(quote(buy = "200000.00"))
        every { investments.findActiveByAccountInFifoOrder(accountId) } returns Outcome.Success(listOf(a, b))
    }

    private fun savesSucceed() {
        every { investments.close(any()) } answers { Outcome.Success(firstArg()) }
        every { investments.save(any()) } answers { Outcome.Success(firstArg()) }
        every { accounts.save(any()) } answers { Outcome.Success(firstArg()) }
        every { transactions.saveAll(any()) } returns Outcome.Success(Unit)
        every { notifications.publish(any()) } returns Outcome.Success(Unit)
    }

    private fun verifyNothingWritten() {
        verify(exactly = 0) { investments.close(any()) }
        verify(exactly = 0) { investments.save(any()) }
        verify(exactly = 0) { accounts.save(any()) }
        verify(exactly = 0) { transactions.saveAll(any()) }
        verify(exactly = 0) { notifications.publish(any()) }
    }

    private fun <T> inTx(value: T): T {
        assertThat(runner.isInsideTransaction).describedAs("port called inside TransactionRunner.run").isTrue()
        return value
    }

    private fun investment(
        purchasedAt: Instant,
        fifoSequence: Long,
        sats: Long,
    ) = Investment.purchase(accountId, purchasedAt, fifoSequence, Money.ZERO, price("400000.00"), BtcAmount(sats))

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

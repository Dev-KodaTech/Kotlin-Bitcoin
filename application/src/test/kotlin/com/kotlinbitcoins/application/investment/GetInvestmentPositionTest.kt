package com.kotlinbitcoins.application.investment

import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.InvestmentRepository
import com.kotlinbitcoins.application.quote.QuoteError
import com.kotlinbitcoins.application.quote.QuoteService
import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.investment.Investment
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class GetInvestmentPositionTest {
    private val accountId = AccountId.random()
    private val accounts = mockk<AccountRepository>()
    private val investments = mockk<InvestmentRepository>()
    private val quoteService = mockk<QuoteService>()

    private val getInvestmentPosition = GetInvestmentPosition(accounts, investments, quoteService)

    @BeforeEach
    fun theAccountExists() {
        every { accounts.findById(accountId) } returns Outcome.Success(mockk<Account>())
    }

    private val quote = Quote(price("440000.00"), price("441000.00"), Instant.parse("2026-11-09T11:10:00Z"))

    @Test
    fun `each item carries the purchase data, the variation and the current value from the buy price`() {
        val purchasedAt = Instant.parse("2026-11-01T12:00:00Z")
        val investment = investment(purchasedAt, 0, "400.00", "400000.00", 100_000)
        every { investments.findActiveByAccountInFifoOrder(accountId) } returns Outcome.Success(listOf(investment))
        every { quoteService.currentQuote() } returns Outcome.Success(quote)

        val result = getInvestmentPosition.execute(accountId)

        val position = (result as Outcome.Success).value
        assertThat(position.quote).isEqualTo(quote)
        assertThat(position.items).hasSize(1)
        val item = position.items.single()
        assertThat(item.investmentId).isEqualTo(investment.id)
        assertThat(item.purchasedAt).isEqualTo(purchasedAt)
        assertThat(item.investedAmount).isEqualTo(money("400.00"))
        assertThat(item.purchaseQuote).isEqualTo(price("400000.00"))
        assertThat(item.btcAmount).isEqualTo(BtcAmount(100_000))
        assertThat(item.variationPercent.toPlainString()).isEqualTo("10.00")
        assertThat(item.currentValue).isEqualTo(money("440.00"))
        assertThat(position.totalCurrentValue).isEqualTo(money("440.00"))
    }

    @Test
    fun `the total is the sum of the values already rounded down to the cent`() {
        // 33_333 sats × 440000.00 ÷ 10⁸ = 146.66520 → 146.66 ; 33_333 sats again → 146.66 ; sum 293.32
        // (the sum of the unrounded values would be 293.3304 → 293.33)
        val first = investment(Instant.parse("2026-11-01T12:00:00Z"), 0, "100.00", "300000.00", 33_333)
        val second = investment(Instant.parse("2026-11-02T12:00:00Z"), 1, "100.00", "300000.00", 33_333)
        every { investments.findActiveByAccountInFifoOrder(accountId) } returns Outcome.Success(listOf(first, second))
        every { quoteService.currentQuote() } returns Outcome.Success(quote)

        val position = (getInvestmentPosition.execute(accountId) as Outcome.Success).value

        assertThat(position.items.map { it.currentValue }).containsExactly(money("146.66"), money("146.66"))
        assertThat(position.totalCurrentValue).isEqualTo(money("293.32"))
    }

    @Test
    fun `keeps the FIFO order given by the repository`() {
        val older = investment(Instant.parse("2026-11-01T12:00:00Z"), 0, "100.00", "400000.00", 25_000)
        val newer = investment(Instant.parse("2026-11-02T12:00:00Z"), 1, "200.00", "400000.00", 50_000)
        every { investments.findActiveByAccountInFifoOrder(accountId) } returns Outcome.Success(listOf(older, newer))
        every { quoteService.currentQuote() } returns Outcome.Success(quote)

        val position = (getInvestmentPosition.execute(accountId) as Outcome.Success).value

        assertThat(position.items.map { it.investmentId }).containsExactly(older.id, newer.id)
    }

    @Test
    fun `asks for one single quote however many investments there are`() {
        val list = (0L..4L).map { investment(Instant.parse("2026-11-01T12:00:00Z"), it, "10.00", "400000.00", 2_500) }
        every { investments.findActiveByAccountInFifoOrder(accountId) } returns Outcome.Success(list)
        every { quoteService.currentQuote() } returns Outcome.Success(quote)

        getInvestmentPosition.execute(accountId)

        verify(exactly = 1) { quoteService.currentQuote() }
    }

    @Test
    fun `without investments the list is empty and the total is zero`() {
        every { investments.findActiveByAccountInFifoOrder(accountId) } returns Outcome.Success(emptyList())
        every { quoteService.currentQuote() } returns Outcome.Success(quote)

        val position = (getInvestmentPosition.execute(accountId) as Outcome.Success).value

        assertThat(position.items).isEmpty()
        assertThat(position.totalCurrentValue).isEqualTo(Money.ZERO)
        assertThat(position.quote).isEqualTo(quote)
    }

    @Test
    fun `QuoteUnavailable is propagated`() {
        every { investments.findActiveByAccountInFifoOrder(accountId) } returns Outcome.Success(emptyList())
        every { quoteService.currentQuote() } returns Outcome.Failure(QuoteError.QuoteUnavailable)

        val result = getInvestmentPosition.execute(accountId)

        assertThat(result).isEqualTo(Outcome.Failure(GetInvestmentPositionError.QuoteUnavailable))
    }

    @Test
    fun `failure loading the investments is ServiceUnavailable and writes nothing`() {
        every { investments.findActiveByAccountInFifoOrder(accountId) } returns
            Outcome.Failure(InfrastructureFailure("db down"))
        every { quoteService.currentQuote() } returns Outcome.Success(quote)

        val result = getInvestmentPosition.execute(accountId)

        assertThat(result).isEqualTo(Outcome.Failure(GetInvestmentPositionError.ServiceUnavailable))
        verify(exactly = 0) { investments.save(any()) }
        verify(exactly = 0) { investments.nextFifoSequence() }
    }

    @Test
    fun `an account that does not exist is AccountNotFound and nothing else is read`() {
        every { accounts.findById(accountId) } returns Outcome.Success(null)

        val result = getInvestmentPosition.execute(accountId)

        assertThat(result).isEqualTo(Outcome.Failure(GetInvestmentPositionError.AccountNotFound))
        verify(exactly = 0) { investments.findActiveByAccountInFifoOrder(any()) }
        verify(exactly = 0) { quoteService.currentQuote() }
    }

    @Test
    fun `failure loading the account is ServiceUnavailable`() {
        every { accounts.findById(accountId) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = getInvestmentPosition.execute(accountId)

        assertThat(result).isEqualTo(Outcome.Failure(GetInvestmentPositionError.ServiceUnavailable))
        verify(exactly = 0) { investments.findActiveByAccountInFifoOrder(any()) }
    }

    private fun investment(
        purchasedAt: Instant,
        fifoSequence: Long,
        invested: String,
        purchaseQuote: String,
        sats: Long,
    ): Investment =
        Investment.purchase(
            accountId,
            purchasedAt,
            fifoSequence,
            money(invested),
            price(purchaseQuote),
            BtcAmount(sats),
        )

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}

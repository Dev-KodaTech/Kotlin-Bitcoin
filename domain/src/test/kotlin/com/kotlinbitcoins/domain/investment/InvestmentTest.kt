package com.kotlinbitcoins.domain.investment

import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class InvestmentTest {
    private val accountId = AccountId.random()
    private val purchasedAt = Instant.parse("2026-11-09T11:10:00Z")

    @Test
    fun `purchase creates an active investment that originates from a purchase`() {
        val investment =
            Investment.purchase(
                accountId = accountId,
                purchasedAt = purchasedAt,
                fifoSequence = 7,
                investedAmount = money("400.00"),
                purchaseQuote = price("400000.00"),
                btcAmount = BtcAmount(100_000),
            )

        assertThat(investment.accountId).isEqualTo(accountId)
        assertThat(investment.purchasedAt).isEqualTo(purchasedAt)
        assertThat(investment.fifoSequence).isEqualTo(7)
        assertThat(investment.investedAmount).isEqualTo(money("400.00"))
        assertThat(investment.purchaseQuote).isEqualTo(price("400000.00"))
        assertThat(investment.btcAmount).isEqualTo(BtcAmount(100_000))
        assertThat(investment.status).isEqualTo(InvestmentStatus.ACTIVE)
        assertThat(investment.origin).isEqualTo(InvestmentOrigin.PURCHASE)
        assertThat(investment.originInvestmentId).isNull()
        assertThat(investment.closedAt).isNull()
    }

    @Test
    fun `each purchase gets its own id`() {
        val first = purchase(BtcAmount(1))
        val second = purchase(BtcAmount(1))

        assertThat(first.id).isNotEqualTo(second.id)
    }

    @Test
    fun `an investment without bitcoin is rejected`() {
        assertThatThrownBy { purchase(BtcAmount.ZERO) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `close turns the investment into CLOSED and records when`() {
        val closedAt = Instant.parse("2026-11-10T09:00:00Z")
        val investment = purchase(BtcAmount(100_000))

        val closed = investment.close(closedAt)

        assertThat(closed.status).isEqualTo(InvestmentStatus.CLOSED)
        assertThat(closed.closedAt).isEqualTo(closedAt)
        assertThat(closed.copy(status = InvestmentStatus.ACTIVE, closedAt = null)).isEqualTo(investment)
        assertThat(investment.status).isEqualTo(InvestmentStatus.ACTIVE)
    }

    @Test
    fun `closing a closed investment breaks an invariant`() {
        val closed = purchase(BtcAmount(100_000)).close(Instant.parse("2026-11-10T09:00:00Z"))

        assertThatThrownBy { closed.close(Instant.parse("2026-11-10T10:00:00Z")) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `reinvestmentOf keeps the date, the FIFO sequence and the quote of the original`() {
        val original =
            Investment.purchase(accountId, purchasedAt, 7, money("400.00"), price("400000.00"), BtcAmount(100_000))

        val reinvestment = Investment.reinvestmentOf(original, BtcAmount(25_000))

        assertThat(reinvestment.id).isNotEqualTo(original.id)
        assertThat(reinvestment.accountId).isEqualTo(accountId)
        assertThat(reinvestment.purchasedAt).isEqualTo(purchasedAt)
        assertThat(reinvestment.fifoSequence).isEqualTo(7)
        assertThat(reinvestment.purchaseQuote).isEqualTo(price("400000.00"))
        assertThat(reinvestment.btcAmount).isEqualTo(BtcAmount(25_000))
        assertThat(reinvestment.status).isEqualTo(InvestmentStatus.ACTIVE)
        assertThat(reinvestment.origin).isEqualTo(InvestmentOrigin.REINVESTMENT)
        assertThat(reinvestment.originInvestmentId).isEqualTo(original.id)
        assertThat(reinvestment.closedAt).isNull()
    }

    @Test
    fun `reinvestmentOf invests the residual at the original quote rounded down to the cent`() {
        // 33_333 sats × 300000.00 ÷ 10⁸ = 99.999
        val original =
            Investment.purchase(accountId, purchasedAt, 1, money("200.00"), price("300000.00"), BtcAmount(66_666))

        val reinvestment = Investment.reinvestmentOf(original, BtcAmount(33_333))

        assertThat(reinvestment.investedAmount).isEqualTo(money("99.99"))
    }

    @Test
    fun `reinvestmentOf with no residual is rejected`() {
        val original = purchase(BtcAmount(100_000))

        assertThatThrownBy { Investment.reinvestmentOf(original, BtcAmount.ZERO) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun purchase(btcAmount: BtcAmount) =
        Investment.purchase(accountId, purchasedAt, 1, money("1.00"), price("400000.00"), btcAmount)

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}

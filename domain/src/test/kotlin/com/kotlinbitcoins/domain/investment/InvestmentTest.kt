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

    private fun purchase(btcAmount: BtcAmount) =
        Investment.purchase(accountId, purchasedAt, 1, money("1.00"), price("400000.00"), btcAmount)

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}

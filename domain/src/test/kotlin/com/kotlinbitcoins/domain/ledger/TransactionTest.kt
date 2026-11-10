package com.kotlinbitcoins.domain.ledger

import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvestmentId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import com.kotlinbitcoins.domain.shared.QuotePrice
import com.kotlinbitcoins.domain.shared.RedemptionRequestId
import com.kotlinbitcoins.domain.shared.TransactionId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class TransactionTest {
    private val accountId = AccountId.random()
    private val occurredAt = Instant.parse("2026-11-09T11:10:00Z")

    @Test
    fun `deposit creates a DEPOSIT line without any bitcoin data`() {
        val amount = (PositiveMoney.of(BigDecimal("100.00")) as Outcome.Success).value

        val transaction = Transaction.deposit(accountId, amount, occurredAt)

        assertThat(transaction.type).isEqualTo(TransactionType.DEPOSIT)
        assertThat(transaction.accountId).isEqualTo(accountId)
        assertThat(transaction.amount).isEqualTo(amount.money)
        assertThat(transaction.occurredAt).isEqualTo(occurredAt)
        assertThat(transaction.btcAmount).isNull()
        assertThat(transaction.quote).isNull()
        assertThat(transaction.investmentId).isNull()
        assertThat(transaction.requestId).isNull()
    }

    @Test
    fun `each deposit gets its own id`() {
        val amount = (PositiveMoney.of(BigDecimal("1.00")) as Outcome.Success).value

        val first = Transaction.deposit(accountId, amount, occurredAt)
        val second = Transaction.deposit(accountId, amount, occurredAt)

        assertThat(first.id).isNotEqualTo(second.id)
    }

    @Test
    fun `purchase creates a PURCHASE line with the bitcoin bought, the sell price and the investment`() {
        val amount = (PositiveMoney.of(BigDecimal("400.00")) as Outcome.Success).value
        val sellPrice = (QuotePrice.of(BigDecimal("400000.00")) as Outcome.Success).value
        val investmentId = InvestmentId.random()

        val transaction =
            Transaction.purchase(
                accountId,
                amount,
                BtcAmount(100_000),
                sellPrice,
                investmentId,
                occurredAt,
            )

        assertThat(transaction.type).isEqualTo(TransactionType.PURCHASE)
        assertThat(transaction.accountId).isEqualTo(accountId)
        assertThat(transaction.amount).isEqualTo(amount.money)
        assertThat(transaction.btcAmount).isEqualTo(BtcAmount(100_000))
        assertThat(transaction.quote).isEqualTo(sellPrice)
        assertThat(transaction.investmentId).isEqualTo(investmentId)
        assertThat(transaction.requestId).isNull()
        assertThat(transaction.occurredAt).isEqualTo(occurredAt)
    }

    @Test
    fun `redemption creates a REDEMPTION line with the credit, the bitcoin sold, the buy price and the request`() {
        val buyPrice = (QuotePrice.of(BigDecimal("400000.00")) as Outcome.Success).value
        val credit = (Money.of(BigDecimal("200.00")) as Outcome.Success).value
        val investmentId = InvestmentId.random()
        val requestId = RedemptionRequestId.random()

        val transaction =
            Transaction.redemption(accountId, credit, BtcAmount(50_000), buyPrice, investmentId, requestId, occurredAt)

        assertThat(transaction.type).isEqualTo(TransactionType.REDEMPTION)
        assertThat(transaction.accountId).isEqualTo(accountId)
        assertThat(transaction.amount).isEqualTo(credit)
        assertThat(transaction.btcAmount).isEqualTo(BtcAmount(50_000))
        assertThat(transaction.quote).isEqualTo(buyPrice)
        assertThat(transaction.investmentId).isEqualTo(investmentId)
        assertThat(transaction.requestId).isEqualTo(requestId)
        assertThat(transaction.occurredAt).isEqualTo(occurredAt)
    }

    @Test
    fun `a redemption may credit zero when a few satoshis are worth less than a cent`() {
        val buyPrice = (QuotePrice.of(BigDecimal("400000.00")) as Outcome.Success).value

        val transaction =
            Transaction.redemption(
                accountId,
                Money.ZERO,
                BtcAmount(1),
                buyPrice,
                InvestmentId.random(),
                RedemptionRequestId.random(),
                occurredAt,
            )

        assertThat(transaction.amount).isEqualTo(Money.ZERO)
    }

    @Test
    fun `reinvestment creates a REINVESTMENT line with the residual, the original quote and the new investment`() {
        val originalQuote = (QuotePrice.of(BigDecimal("400000.00")) as Outcome.Success).value
        val invested = (Money.of(BigDecimal("100.00")) as Outcome.Success).value
        val investmentId = InvestmentId.random()
        val requestId = RedemptionRequestId.random()

        val transaction =
            Transaction.reinvestment(
                accountId,
                invested,
                BtcAmount(25_000),
                originalQuote,
                investmentId,
                requestId,
                occurredAt,
            )

        assertThat(transaction.type).isEqualTo(TransactionType.REINVESTMENT)
        assertThat(transaction.amount).isEqualTo(invested)
        assertThat(transaction.btcAmount).isEqualTo(BtcAmount(25_000))
        assertThat(transaction.quote).isEqualTo(originalQuote)
        assertThat(transaction.investmentId).isEqualTo(investmentId)
        assertThat(transaction.requestId).isEqualTo(requestId)
        assertThat(transaction.occurredAt).isEqualTo(occurredAt)
    }

    @Test
    fun `a redemption or reinvestment line without a request id breaks an invariant`() {
        val price = (QuotePrice.of(BigDecimal("400000.00")) as Outcome.Success).value

        listOf(TransactionType.REDEMPTION, TransactionType.REINVESTMENT).forEach { type ->
            assertThatThrownBy {
                Transaction(
                    id = TransactionId.random(),
                    accountId = accountId,
                    type = type,
                    amount = Money.ZERO,
                    btcAmount = BtcAmount(1),
                    quote = price,
                    investmentId = InvestmentId.random(),
                    requestId = null,
                    occurredAt = occurredAt,
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}

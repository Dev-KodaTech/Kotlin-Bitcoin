package com.kotlinbitcoins.domain.ledger

import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import org.assertj.core.api.Assertions.assertThat
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
}

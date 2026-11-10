package com.kotlinbitcoins.domain.account

import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.time.Instant

class AccountTest {
    private val id = AccountId.random()
    private val email = (Email.of("ana@example.com") as Outcome.Success).value
    private val createdAt = Instant.parse("2026-11-09T11:10:00Z")

    @Test
    fun `open creates the account with zero balance`() {
        val result = Account.open(id, "Ana Souza", email, "hash", createdAt)

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        val account = (result as Outcome.Success).value
        assertThat(account.id).isEqualTo(id)
        assertThat(account.name).isEqualTo("Ana Souza")
        assertThat(account.email).isEqualTo(email)
        assertThat(account.passwordHash).isEqualTo("hash")
        assertThat(account.balance).isEqualTo(Money.ZERO)
        assertThat(account.createdAt).isEqualTo(createdAt)
    }

    @Test
    fun `open trims the name`() {
        val account = (Account.open(id, "  Ana  ", email, "hash", createdAt) as Outcome.Success).value

        assertThat(account.name).isEqualTo("Ana")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `open rejects a blank name`(name: String) {
        assertThat(Account.open(id, name, email, "hash", createdAt)).isEqualTo(Outcome.Failure(InvalidName))
    }

    @Test
    fun `open accepts 200 characters and rejects more`() {
        assertThat(Account.open(id, "a".repeat(200), email, "hash", createdAt))
            .isInstanceOf(Outcome.Success::class.java)
        assertThat(Account.open(id, "a".repeat(201), email, "hash", createdAt))
            .isEqualTo(Outcome.Failure(InvalidName))
    }

    @Test
    fun `deposit adds the amount to the balance`() {
        val account = Account(id, "Ana Souza", email, "hash", money("50.00"), createdAt)

        val deposited = account.deposit(positive("100.00"))

        assertThat(deposited.balance).isEqualTo(money("150.00"))
        assertThat(deposited.id).isEqualTo(id)
        assertThat(account.balance).isEqualTo(money("50.00"))
    }

    @Test
    fun `debit subtracts the amount from the balance`() {
        val account = Account(id, "Ana Souza", email, "hash", money("1000.00"), createdAt)

        val result = account.debit(positive("100.00"))

        val debited = (result as Outcome.Success).value
        assertThat(debited.balance).isEqualTo(money("900.00"))
        assertThat(debited.id).isEqualTo(id)
        assertThat(account.balance).isEqualTo(money("1000.00"))
    }

    @Test
    fun `debit of the whole balance leaves zero`() {
        val account = Account(id, "Ana Souza", email, "hash", money("100.00"), createdAt)

        val result = account.debit(positive("100.00"))

        assertThat((result as Outcome.Success).value.balance).isEqualTo(Money.ZERO)
    }

    @Test
    fun `debit above the balance is InsufficientBalance`() {
        val account = Account(id, "Ana Souza", email, "hash", money("100.00"), createdAt)

        val result = account.debit(positive("100.01"))

        assertThat(result).isEqualTo(Outcome.Failure(InsufficientBalance))
        assertThat(account.balance).isEqualTo(money("100.00"))
    }

    @Test
    fun `credit adds the amount to the balance`() {
        val account = Account(id, "Ana Souza", email, "hash", money("100.00"), createdAt)

        val credited = account.credit(money("500.00"))

        assertThat(credited.balance).isEqualTo(money("600.00"))
        assertThat(credited.id).isEqualTo(id)
        assertThat(account.balance).isEqualTo(money("100.00"))
    }

    @Test
    fun `credit of zero keeps the balance`() {
        val account = Account(id, "Ana Souza", email, "hash", money("100.00"), createdAt)

        assertThat(account.credit(Money.ZERO).balance).isEqualTo(money("100.00"))
    }

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    private fun positive(value: String): PositiveMoney = (PositiveMoney.of(BigDecimal(value)) as Outcome.Success).value
}

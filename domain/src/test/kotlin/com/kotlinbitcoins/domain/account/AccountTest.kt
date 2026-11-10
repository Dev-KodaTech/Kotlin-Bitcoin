package com.kotlinbitcoins.domain.account

import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
}

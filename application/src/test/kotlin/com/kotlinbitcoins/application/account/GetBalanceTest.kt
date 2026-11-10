package com.kotlinbitcoins.application.account

import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class GetBalanceTest {
    private val accountId = AccountId.random()
    private val accounts = mockk<AccountRepository>()

    private val getBalance = GetBalance(accounts)

    @Test
    fun `returns the balance of the account`() {
        every { accounts.findById(accountId) } returns Outcome.Success(account("150.50"))

        val result = getBalance.execute(accountId)

        assertThat(result).isEqualTo(Outcome.Success(money("150.50")))
    }

    @Test
    fun `a new account has a zero balance`() {
        every { accounts.findById(accountId) } returns Outcome.Success(account("0.00"))

        val result = getBalance.execute(accountId)

        assertThat(result).isEqualTo(Outcome.Success(money("0.00")))
    }

    @Test
    fun `unknown account is AccountNotFound`() {
        every { accounts.findById(accountId) } returns Outcome.Success(null)

        val result = getBalance.execute(accountId)

        assertThat(result).isEqualTo(Outcome.Failure(GetBalanceError.AccountNotFound))
    }

    @Test
    fun `failure loading the account is ServiceUnavailable and writes nothing`() {
        every { accounts.findById(accountId) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = getBalance.execute(accountId)

        assertThat(result).isEqualTo(Outcome.Failure(GetBalanceError.ServiceUnavailable))
        verify(exactly = 0) { accounts.save(any()) }
        verify(exactly = 0) { accounts.findByIdForUpdate(any()) }
    }

    private fun account(balance: String) =
        Account(
            id = accountId,
            name = "Ana Souza",
            email = (Email.of("ana@example.com") as Outcome.Success).value,
            passwordHash = "hash",
            balance = money(balance),
            createdAt = Instant.parse("2026-11-09T11:10:00Z"),
        )

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value
}

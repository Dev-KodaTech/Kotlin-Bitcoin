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

class GetProfileTest {
    private val accountId = AccountId.random()
    private val accounts = mockk<AccountRepository>()

    private val getProfile = GetProfile(accounts)

    @Test
    fun `returns only the name and the e-mail of the account`() {
        every { accounts.findById(accountId) } returns Outcome.Success(account())

        val result = getProfile.execute(accountId)

        assertThat(result).isEqualTo(Outcome.Success(Profile(name = "Maria Souza", email = email("maria@example.com"))))
    }

    @Test
    fun `unknown account is AccountNotFound`() {
        every { accounts.findById(accountId) } returns Outcome.Success(null)

        val result = getProfile.execute(accountId)

        assertThat(result).isEqualTo(Outcome.Failure(GetProfileError.AccountNotFound))
    }

    @Test
    fun `failure loading the account is ServiceUnavailable and writes nothing`() {
        every { accounts.findById(accountId) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = getProfile.execute(accountId)

        assertThat(result).isEqualTo(Outcome.Failure(GetProfileError.ServiceUnavailable))
        verify(exactly = 0) { accounts.save(any()) }
        verify(exactly = 0) { accounts.findByIdForUpdate(any()) }
    }

    private fun account() =
        Account(
            id = accountId,
            name = "Maria Souza",
            email = email("maria@example.com"),
            passwordHash = "hash",
            balance = (Money.of(BigDecimal("1234.56")) as Outcome.Success).value,
            createdAt = Instant.parse("2026-11-09T11:10:00Z"),
        )

    private fun email(value: String): Email = (Email.of(value) as Outcome.Success).value
}

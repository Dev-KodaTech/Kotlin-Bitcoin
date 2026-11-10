package com.kotlinbitcoins.application.account

import com.kotlinbitcoins.application.account.LoginError.InvalidCredentials
import com.kotlinbitcoins.application.account.LoginError.ServiceUnavailable
import com.kotlinbitcoins.application.port.AccessToken
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.PasswordHasher
import com.kotlinbitcoins.application.port.TokenIssuer
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
import java.time.Instant

class AuthenticateAccountTest {
    private val accounts = mockk<AccountRepository>()
    private val passwordHasher = mockk<PasswordHasher>()
    private val tokenIssuer = mockk<TokenIssuer>()

    private val account =
        Account(AccountId.random(), "Ana Souza", email("ana@example.com"), ACCOUNT_HASH, Money.ZERO, CREATED_AT)
    private val token = AccessToken("jwt-token", 3600)

    init {
        every { passwordHasher.hash(any()) } returns DUMMY_HASH
        every { passwordHasher.matches(any(), any()) } returns false
        every { passwordHasher.matches(PASSWORD, ACCOUNT_HASH) } returns true
        every { accounts.findByEmail(any()) } returns Outcome.Success(null)
        every { accounts.findByEmail(account.email) } returns Outcome.Success(account)
        every { tokenIssuer.issue(account.id) } returns token
    }

    private val authenticateAccount = AuthenticateAccount(accounts, passwordHasher, tokenIssuer)

    @Test
    fun `correct credentials issue an access token for the account`() {
        val result = authenticateAccount.execute(LoginCommand("ana@example.com", PASSWORD))

        assertThat(result).isEqualTo(Outcome.Success(token))
        verify(exactly = 1) { tokenIssuer.issue(account.id) }
    }

    @Test
    fun `the e-mail is normalized before the lookup`() {
        val result = authenticateAccount.execute(LoginCommand(" ANA@example.com ", PASSWORD))

        assertThat(result).isEqualTo(Outcome.Success(token))
    }

    @Test
    fun `unknown e-mail is InvalidCredentials and still checks the password against a dummy hash`() {
        val result = authenticateAccount.execute(LoginCommand("bia@example.com", PASSWORD))

        assertThat(result).isEqualTo(Outcome.Failure(InvalidCredentials))
        verify(exactly = 1) { passwordHasher.matches(PASSWORD, DUMMY_HASH) }
        verify(exactly = 0) { tokenIssuer.issue(any()) }
    }

    @Test
    fun `wrong password is the same InvalidCredentials`() {
        val result = authenticateAccount.execute(LoginCommand("ana@example.com", "wrong-password"))

        assertThat(result).isEqualTo(Outcome.Failure(InvalidCredentials))
        verify(exactly = 1) { passwordHasher.matches("wrong-password", ACCOUNT_HASH) }
        verify(exactly = 0) { tokenIssuer.issue(any()) }
    }

    @Test
    fun `malformed e-mail is InvalidCredentials and still spends a hash comparison`() {
        val result = authenticateAccount.execute(LoginCommand("not-an-email", PASSWORD))

        assertThat(result).isEqualTo(Outcome.Failure(InvalidCredentials))
        verify(exactly = 1) { passwordHasher.matches(any(), DUMMY_HASH) }
        verify(exactly = 0) { accounts.findByEmail(any()) }
    }

    @Test
    fun `password over 72 bytes is InvalidCredentials without passing it to matches`() {
        val longPassword = "é".repeat(37)

        val result = authenticateAccount.execute(LoginCommand("ana@example.com", longPassword))

        assertThat(result).isEqualTo(Outcome.Failure(InvalidCredentials))
        verify(exactly = 0) { passwordHasher.matches(longPassword, any()) }
        verify(exactly = 1) { passwordHasher.matches(any(), DUMMY_HASH) }
        verify(exactly = 0) { tokenIssuer.issue(any()) }
    }

    @Test
    fun `infrastructure failure on the lookup is ServiceUnavailable`() {
        every { accounts.findByEmail(any()) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = authenticateAccount.execute(LoginCommand("ana@example.com", PASSWORD))

        assertThat(result).isEqualTo(Outcome.Failure(ServiceUnavailable))
        verify(exactly = 0) { tokenIssuer.issue(any()) }
    }

    @Test
    fun `the command never prints the password`() {
        assertThat(LoginCommand("ana@example.com", PASSWORD).toString()).doesNotContain(PASSWORD)
    }

    private fun email(value: String): Email = (Email.of(value) as Outcome.Success).value

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-11-09T11:10:00Z")
        const val PASSWORD = "s3nha-forte"
        const val ACCOUNT_HASH = "\$2a\$12\$accounthashaccounthashaccounthashaccounthashaccount"
        const val DUMMY_HASH = "\$2a\$12\$dummyhashdummyhashdummyhashdummyhashdummyhashdummyh"
    }
}

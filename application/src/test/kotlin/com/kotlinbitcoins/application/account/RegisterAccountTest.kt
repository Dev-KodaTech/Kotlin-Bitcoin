package com.kotlinbitcoins.application.account

import com.kotlinbitcoins.application.account.RegisterAccountError.EmailAlreadyInUse
import com.kotlinbitcoins.application.account.RegisterAccountError.InvalidInput
import com.kotlinbitcoins.application.account.RegisterAccountError.ServiceUnavailable
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.PasswordHasher
import com.kotlinbitcoins.application.port.SaveAccountError
import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class RegisterAccountTest {
    private val accounts = mockk<AccountRepository>()
    private val passwordHasher = mockk<PasswordHasher>()
    private val clock = Clock { NOW }
    private val registerAccount = RegisterAccount(accounts, passwordHasher, clock)

    private val saved = slot<Account>()

    init {
        every { passwordHasher.hash(any()) } returns HASH
        every { accounts.existsByEmail(any()) } returns Outcome.Success(false)
        every { accounts.save(capture(saved)) } answers { Outcome.Success(firstArg()) }
    }

    @Test
    fun `creates the account with zero balance, normalized e-mail and only the password hash`() {
        val result = registerAccount.execute(command(name = "  Ana Souza ", email = " ANA@Example.com "))

        val account = (result as Outcome.Success).value
        assertThat(account.name).isEqualTo("Ana Souza")
        assertThat(account.email.value).isEqualTo("ana@example.com")
        assertThat(account.balance).isEqualTo(Money.ZERO)
        assertThat(account.createdAt).isEqualTo(NOW)
        verify(exactly = 1) { passwordHasher.hash(PASSWORD) }
        assertThat(saved.captured.passwordHash).isEqualTo(HASH)
        assertThat(saved.captured.id).isEqualTo(account.id)
        assertThat(saved.captured.email.value).isEqualTo("ana@example.com")
    }

    @Test
    fun `the returned account never exposes the password hash`() {
        val account = (registerAccount.execute(command()) as Outcome.Success).value

        assertThat(account.toString()).doesNotContain(HASH).doesNotContain(PASSWORD)
        assertThat(RegisteredAccount::class.java.declaredFields.map { it.name })
            .noneMatch { it.contains("password", ignoreCase = true) }
    }

    @Test
    fun `existing e-mail is rejected without saving, comparing the normalized e-mail`() {
        every { accounts.existsByEmail(email("ana@example.com")) } returns Outcome.Success(true)

        val result = registerAccount.execute(command(email = "  ANA@example.COM "))

        assertThat(result).isEqualTo(Outcome.Failure(EmailAlreadyInUse))
        verify(exactly = 0) { accounts.save(any()) }
    }

    @Test
    fun `concurrent registration caught by the unique constraint is EmailAlreadyInUse`() {
        every { accounts.save(any()) } returns Outcome.Failure(SaveAccountError.EmailTaken)

        assertThat(registerAccount.execute(command())).isEqualTo(Outcome.Failure(EmailAlreadyInUse))
    }

    @Test
    fun `lists every invalid field at once`() {
        val result = registerAccount.execute(RegisterAccountCommand(name = "   ", email = "ana@", password = "1234567"))

        assertThat(result).isEqualTo(
            Outcome.Failure(
                InvalidInput(
                    listOf(
                        FieldError("name", Violation.REQUIRED),
                        FieldError("email", Violation.INVALID_FORMAT),
                        FieldError("password", Violation.TOO_SHORT),
                    ),
                ),
            ),
        )
        verify(exactly = 0) { accounts.existsByEmail(any()) }
        verify(exactly = 0) { passwordHasher.hash(any()) }
        verify(exactly = 0) { accounts.save(any()) }
    }

    @Test
    fun `name longer than 200 characters is invalid`() {
        val result = registerAccount.execute(command(name = "a".repeat(201)))

        assertThat(result).isEqualTo(Outcome.Failure(InvalidInput(listOf(FieldError("name", Violation.TOO_LONG)))))
    }

    @Test
    fun `blank e-mail is required`() {
        val result = registerAccount.execute(command(email = "  "))

        assertThat(result).isEqualTo(Outcome.Failure(InvalidInput(listOf(FieldError("email", Violation.REQUIRED)))))
    }

    @Test
    fun `password with more than 72 bytes in UTF-8 is invalid even with few characters`() {
        // 37 x "é" = 37 characters but 74 bytes in UTF-8.
        val result = registerAccount.execute(command(password = "é".repeat(37)))

        assertThat(result).isEqualTo(
            Outcome.Failure(InvalidInput(listOf(FieldError("password", Violation.TOO_LONG)))),
        )
        verify(exactly = 0) { passwordHasher.hash(any()) }
    }

    @Test
    fun `password limits are 8 characters and 72 bytes inclusive`() {
        assertThat(registerAccount.execute(command(password = "12345678"))).isInstanceOf(Outcome.Success::class.java)
        assertThat(
            registerAccount.execute(command(password = "a".repeat(72))),
        ).isInstanceOf(Outcome.Success::class.java)
        assertThat(registerAccount.execute(command(password = "é".repeat(8)))).isInstanceOf(Outcome.Success::class.java)
    }

    @Test
    fun `infrastructure failure on the uniqueness check is ServiceUnavailable without saving`() {
        every { accounts.existsByEmail(any()) } returns Outcome.Failure(InfrastructureFailure("db down"))

        assertThat(registerAccount.execute(command())).isEqualTo(Outcome.Failure(ServiceUnavailable))
        verify(exactly = 0) { accounts.save(any()) }
    }

    @Test
    fun `infrastructure failure on save is ServiceUnavailable`() {
        every { accounts.save(any()) } returns
            Outcome.Failure(SaveAccountError.Infrastructure(InfrastructureFailure("db down")))

        assertThat(registerAccount.execute(command())).isEqualTo(Outcome.Failure(ServiceUnavailable))
    }

    @Test
    fun `the command never prints the password`() {
        assertThat(command().toString()).doesNotContain(PASSWORD)
    }

    private fun command(
        name: String = "Ana Souza",
        email: String = "ana@example.com",
        password: String = PASSWORD,
    ) = RegisterAccountCommand(name, email, password)

    private fun email(value: String): Email = (Email.of(value) as Outcome.Success).value

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-09T11:10:00Z")
        const val PASSWORD = "s3nha-forte"
        const val HASH = "\$2a\$12\$fakehashfakehashfakehashfakehashfakehashfakehashfake"
    }
}

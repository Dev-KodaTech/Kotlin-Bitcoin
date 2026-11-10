package com.kotlinbitcoins.application.account

import com.kotlinbitcoins.application.account.RegisterAccountError.EmailAlreadyInUse
import com.kotlinbitcoins.application.account.RegisterAccountError.InvalidInput
import com.kotlinbitcoins.application.account.RegisterAccountError.ServiceUnavailable
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.PasswordHasher
import com.kotlinbitcoins.application.port.SaveAccountError
import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.flatMap
import com.kotlinbitcoins.domain.shared.map
import com.kotlinbitcoins.domain.shared.mapError
import java.time.Instant

data class RegisterAccountCommand(
    val name: String,
    val email: String,
    val password: String,
) {
    override fun toString(): String = "RegisterAccountCommand(name=$name, email=$email, password=****)"
}

/** The account as the client may see it: never carries the password hash (FR-007). */
data class RegisteredAccount(
    val id: AccountId,
    val name: String,
    val email: Email,
    val balance: Money,
    val createdAt: Instant,
)

/**
 * Registers an account with zero balance (US1, FR-001..FR-003). All invalid fields are reported together; the
 * e-mail is normalized before the uniqueness check, and a concurrent registration that slips past that check is
 * caught by the database unique constraint and reported the same way.
 */
class RegisterAccount(
    private val accounts: AccountRepository,
    private val passwordHasher: PasswordHasher,
    private val clock: Clock,
) {
    fun execute(command: RegisterAccountCommand): Outcome<RegisterAccountError, RegisteredAccount> =
        validate(command)
            .flatMap(::ensureEmailAvailable)
            .flatMap { email -> open(command, email) }
            .flatMap(::save)
            .map(::toRegistered)

    private fun validate(command: RegisterAccountCommand): Outcome<RegisterAccountError, Email> {
        val email = Email.of(command.email)
        val errors =
            listOfNotNull(
                nameViolation(command.name)?.let { FieldError(NAME, it) },
                emailViolation(command.email, email)?.let { FieldError(EMAIL, it) },
                PasswordPolicy.violationOf(command.password)?.let { FieldError(PASSWORD, it) },
            )
        return if (errors.isEmpty() && email is Outcome.Success) {
            Outcome.Success(email.value)
        } else {
            Outcome.Failure(InvalidInput(errors))
        }
    }

    private fun ensureEmailAvailable(email: Email): Outcome<RegisterAccountError, Email> =
        accounts
            .existsByEmail(email)
            .mapError<_, _, RegisterAccountError> { ServiceUnavailable }
            .flatMap { isTaken -> if (isTaken) Outcome.Failure(EmailAlreadyInUse) else Outcome.Success(email) }

    private fun open(
        command: RegisterAccountCommand,
        email: Email,
    ): Outcome<RegisterAccountError, Account> =
        Account
            .open(AccountId.random(), command.name, email, passwordHasher.hash(command.password), clock.now())
            .mapError { InvalidInput(listOf(FieldError(NAME, Violation.REQUIRED))) }

    private fun save(account: Account): Outcome<RegisterAccountError, Account> =
        accounts.save(account).mapError { error ->
            when (error) {
                SaveAccountError.EmailTaken -> EmailAlreadyInUse
                is SaveAccountError.Infrastructure -> ServiceUnavailable
            }
        }

    private fun toRegistered(account: Account) =
        RegisteredAccount(account.id, account.name, account.email, account.balance, account.createdAt)

    private fun nameViolation(raw: String): Violation? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> Violation.REQUIRED
            name.length > Account.MAX_NAME_LENGTH -> Violation.TOO_LONG
            else -> null
        }
    }

    private fun emailViolation(
        raw: String,
        email: Outcome<*, Email>,
    ): Violation? =
        when {
            raw.isBlank() -> Violation.REQUIRED
            email is Outcome.Failure -> Violation.INVALID_FORMAT
            else -> null
        }

    private companion object {
        const val NAME = "name"
        const val EMAIL = "email"
        const val PASSWORD = "password"
    }
}

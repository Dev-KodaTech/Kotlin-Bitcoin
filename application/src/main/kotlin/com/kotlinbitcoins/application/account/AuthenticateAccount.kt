package com.kotlinbitcoins.application.account

import com.kotlinbitcoins.application.account.LoginError.InvalidCredentials
import com.kotlinbitcoins.application.account.LoginError.ServiceUnavailable
import com.kotlinbitcoins.application.port.AccessToken
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.PasswordHasher
import com.kotlinbitcoins.application.port.TokenIssuer
import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.flatMap
import com.kotlinbitcoins.domain.shared.mapError

data class LoginCommand(
    val email: String,
    val password: String,
) {
    override fun toString(): String = "LoginCommand(email=$email, password=****)"
}

/**
 * Checks e-mail and password and issues an access token (US1, FR-004, FR-005).
 *
 * Every rejection is the same [InvalidCredentials], and every path spends one hash comparison: when there is no
 * account to compare against (unknown or malformed e-mail, password over the BCrypt limit) the comparison runs
 * against a dummy hash, so the response time does not reveal whether the e-mail exists (R-09). A password over
 * the limit is never handed to the hasher.
 */
class AuthenticateAccount(
    private val accounts: AccountRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenIssuer: TokenIssuer,
) {
    private val dummyHash: String = passwordHasher.hash(DUMMY_PASSWORD)

    fun execute(command: LoginCommand): Outcome<LoginError, AccessToken> {
        val email = Email.of(command.email)
        if (email !is Outcome.Success || PasswordPolicy.exceedsMaxBytes(command.password)) {
            return rejectAfterDummyCheck(DUMMY_PASSWORD)
        }
        return accounts
            .findByEmail(email.value)
            .mapError<_, _, LoginError> { ServiceUnavailable }
            .flatMap { account -> verify(account, command.password) }
    }

    private fun verify(
        account: Account?,
        password: String,
    ): Outcome<LoginError, AccessToken> =
        when {
            account == null -> rejectAfterDummyCheck(password)
            passwordHasher.matches(password, account.passwordHash) -> Outcome.Success(tokenIssuer.issue(account.id))
            else -> Outcome.Failure(InvalidCredentials)
        }

    private fun rejectAfterDummyCheck(password: String): Outcome<LoginError, Nothing> {
        passwordHasher.matches(password, dummyHash)
        return Outcome.Failure(InvalidCredentials)
    }

    private companion object {
        const val DUMMY_PASSWORD = "timing-equalization-only"
    }
}

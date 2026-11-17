package com.kotlinbitcoins.application.account

import com.kotlinbitcoins.application.account.GetProfileError.AccountNotFound
import com.kotlinbitcoins.application.account.GetProfileError.ServiceUnavailable
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.InfrastructureUnavailable
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.flatMap
import com.kotlinbitcoins.domain.shared.map
import com.kotlinbitcoins.domain.shared.mapError

/** What the customer may see of their own account: no id, balance or credential (FR-036). */
data class Profile(
    val name: String,
    val email: Email,
)

sealed interface GetProfileError {
    /** The authenticated account no longer exists, so the token no longer stands for anyone. */
    data object AccountNotFound : GetProfileError

    data object ServiceUnavailable : GetProfileError, InfrastructureUnavailable
}

/**
 * Reads the name and e-mail of the authenticated account (FR-036, amendment `GET /me` asked by 002-frontend-web).
 * A read has no side effect, so it needs neither a transaction nor the account lock.
 */
class GetProfile(
    private val accounts: AccountRepository,
) {
    fun execute(accountId: AccountId): Outcome<GetProfileError, Profile> =
        accounts
            .findById(accountId)
            .mapError<_, _, GetProfileError> { ServiceUnavailable }
            .flatMap { account -> if (account == null) Outcome.Failure(AccountNotFound) else Outcome.Success(account) }
            .map { Profile(name = it.name, email = it.email) }
}

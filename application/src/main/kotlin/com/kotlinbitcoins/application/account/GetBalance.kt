package com.kotlinbitcoins.application.account

import com.kotlinbitcoins.application.account.GetBalanceError.AccountNotFound
import com.kotlinbitcoins.application.account.GetBalanceError.ServiceUnavailable
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.InfrastructureUnavailable
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.flatMap
import com.kotlinbitcoins.domain.shared.map
import com.kotlinbitcoins.domain.shared.mapError

sealed interface GetBalanceError {
    /** The authenticated account no longer exists, so the token no longer stands for anyone. */
    data object AccountNotFound : GetBalanceError

    data object ServiceUnavailable : GetBalanceError, InfrastructureUnavailable
}

/**
 * Reads the balance in reais of the authenticated account (US3, FR-009). A read has no side effect, so it needs
 * neither a transaction nor the account lock.
 */
class GetBalance(
    private val accounts: AccountRepository,
) {
    fun execute(accountId: AccountId): Outcome<GetBalanceError, Money> =
        accounts
            .findById(accountId)
            .mapError<_, _, GetBalanceError> { ServiceUnavailable }
            .flatMap { account -> if (account == null) Outcome.Failure(AccountNotFound) else Outcome.Success(account) }
            .map { it.balance }
}

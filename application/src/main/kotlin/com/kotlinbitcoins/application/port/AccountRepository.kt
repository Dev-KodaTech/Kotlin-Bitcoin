package com.kotlinbitcoins.application.port

import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Outcome

sealed interface SaveAccountError {
    /** Another account already uses the e-mail, including concurrent registrations (FR-002). */
    data object EmailTaken : SaveAccountError

    data class Infrastructure(
        val failure: InfrastructureFailure,
    ) : SaveAccountError
}

interface AccountRepository {
    fun save(account: Account): Outcome<SaveAccountError, Account>

    fun findById(id: AccountId): Outcome<InfrastructureFailure, Account?>

    /** Loads the account holding a pessimistic lock until the current transaction ends (R-05). */
    fun findByIdForUpdate(id: AccountId): Outcome<InfrastructureFailure, Account?>

    fun findByEmail(email: Email): Outcome<InfrastructureFailure, Account?>

    fun existsByEmail(email: Email): Outcome<InfrastructureFailure, Boolean>
}

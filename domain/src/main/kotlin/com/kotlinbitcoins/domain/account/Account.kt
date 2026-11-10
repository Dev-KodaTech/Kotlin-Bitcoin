package com.kotlinbitcoins.domain.account

import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import java.time.Instant

data object InvalidName

/**
 * Client account. The balance starts at zero and is never negative (FR-001, FR-010).
 * Client input goes through [open], which reports an invalid name as a value; the constructor only rebuilds
 * accounts that are already valid (e.g. loaded from storage) and treats a broken invariant as a programming error.
 */
data class Account(
    val id: AccountId,
    val name: String,
    val email: Email,
    val passwordHash: String,
    val balance: Money,
    val createdAt: Instant,
) {
    init {
        require(isValidName(name)) { "Account name must be trimmed, non-blank and at most $MAX_NAME_LENGTH chars" }
    }

    override fun toString(): String = "Account(id=$id, email=$email, balance=$balance, createdAt=$createdAt)"

    companion object {
        const val MAX_NAME_LENGTH = 200

        fun open(
            id: AccountId,
            name: String,
            email: Email,
            passwordHash: String,
            createdAt: Instant,
        ): Outcome<InvalidName, Account> {
            val trimmed = name.trim()
            return if (isValidName(trimmed)) {
                Outcome.Success(Account(id, trimmed, email, passwordHash, Money.ZERO, createdAt))
            } else {
                Outcome.Failure(InvalidName)
            }
        }

        private fun isValidName(name: String): Boolean =
            name.isNotEmpty() && name.length <= MAX_NAME_LENGTH && name == name.trim()
    }
}

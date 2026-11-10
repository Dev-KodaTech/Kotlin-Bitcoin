package com.kotlinbitcoins.infrastructure.persistence.account

import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import org.springframework.dao.DataRetrievalFailureException

/** Converts between the domain [Account] and its table row. A row that breaks a domain invariant is corrupt data. */
object AccountMapper {
    fun toEntity(account: Account): AccountJpaEntity =
        AccountJpaEntity(
            id = account.id.value,
            name = account.name,
            email = account.email.value,
            passwordHash = account.passwordHash,
            balance = account.balance.amount,
            createdAt = account.createdAt,
        )

    fun toDomain(entity: AccountJpaEntity): Account {
        val email = Email.of(entity.email) as? Outcome.Success ?: corrupt(entity, "email")
        val balance = Money.of(entity.balance) as? Outcome.Success ?: corrupt(entity, "balance")
        return Account(
            id = AccountId(entity.id),
            name = entity.name,
            email = email.value,
            passwordHash = entity.passwordHash,
            balance = balance.value,
            createdAt = entity.createdAt,
        )
    }

    private fun corrupt(
        entity: AccountJpaEntity,
        column: String,
    ): Nothing = throw DataRetrievalFailureException("Account ${entity.id} has an invalid $column")
}

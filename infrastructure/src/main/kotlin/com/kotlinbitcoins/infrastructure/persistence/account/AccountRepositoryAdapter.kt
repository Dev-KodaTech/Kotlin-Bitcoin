package com.kotlinbitcoins.infrastructure.persistence.account

import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.SaveAccountError
import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.infrastructure.persistence.isPersistenceFailure
import com.kotlinbitcoins.infrastructure.persistence.persistenceCall
import com.kotlinbitcoins.infrastructure.persistence.toInfrastructureFailure
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException

/**
 * [AccountRepository] over Spring Data JPA. No exception leaves this class: the unique e-mail violation becomes
 * [SaveAccountError.EmailTaken] and every other database error becomes an [InfrastructureFailure].
 */
class AccountRepositoryAdapter(
    private val repository: SpringDataAccountRepository,
) : AccountRepository {
    override fun save(account: Account): Outcome<SaveAccountError, Account> =
        try {
            // Flush now so constraint violations surface here, not at commit time.
            repository.saveAndFlush(AccountMapper.toEntity(account))
            Outcome.Success(account)
        } catch (exception: DataIntegrityViolationException) {
            if (exception.isEmailUniqueViolation()) {
                Outcome.Failure(SaveAccountError.EmailTaken)
            } else {
                Outcome.Failure(SaveAccountError.Infrastructure(toInfrastructureFailure("account.save", exception)))
            }
        } catch (exception: RuntimeException) {
            if (!isPersistenceFailure(exception)) throw exception
            Outcome.Failure(SaveAccountError.Infrastructure(toInfrastructureFailure("account.save", exception)))
        }

    override fun findById(id: AccountId): Outcome<InfrastructureFailure, Account?> =
        persistenceCall("account.findById") {
            repository.findById(id.value).map(AccountMapper::toDomain).orElse(null)
        }

    override fun findByIdForUpdate(id: AccountId): Outcome<InfrastructureFailure, Account?> =
        persistenceCall("account.findByIdForUpdate") {
            repository.findLockedById(id.value)?.let(AccountMapper::toDomain)
        }

    override fun findByEmail(email: Email): Outcome<InfrastructureFailure, Account?> =
        persistenceCall("account.findByEmail") {
            repository.findByEmail(email.value)?.let(AccountMapper::toDomain)
        }

    override fun existsByEmail(email: Email): Outcome<InfrastructureFailure, Boolean> =
        persistenceCall("account.existsByEmail") { repository.existsByEmail(email.value) }

    private fun DataIntegrityViolationException.isEmailUniqueViolation(): Boolean =
        generateSequence<Throwable>(this) { it.cause }
            .filterIsInstance<ConstraintViolationException>()
            .any { it.constraintName.equals(EMAIL_UNIQUE_CONSTRAINT, ignoreCase = true) }

    private companion object {
        const val EMAIL_UNIQUE_CONSTRAINT = "uq_accounts_email"
    }
}

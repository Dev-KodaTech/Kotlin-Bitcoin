package com.kotlinbitcoins.application.ledger

import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.domain.ledger.Transaction
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.flatMap
import com.kotlinbitcoins.domain.shared.map
import com.kotlinbitcoins.domain.shared.mapError
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Read model of US8: the period `[from, to)` that was queried and its lines, newest first. */
data class Statement(
    val from: Instant,
    val to: Instant,
    val entries: List<Transaction>,
)

/**
 * Lists the deposits, purchases, redemptions and reinvestments of the account in a period (US8, FR-023..FR-025).
 * A read has no side effect, so it needs neither a transaction nor the account lock.
 */
class GetStatement(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val clock: Clock,
    private val businessZone: ZoneId,
) {
    fun execute(
        accountId: AccountId,
        from: LocalDate?,
        to: LocalDate?,
    ): Outcome<StatementError, Statement> =
        accounts
            .findById(accountId)
            .mapError<_, _, StatementError> { StatementError.ServiceUnavailable }
            .flatMap { account ->
                if (account ==
                    null
                ) {
                    Outcome.Failure(StatementError.AccountNotFound)
                } else {
                    Outcome.Success(account)
                }
            }.flatMap { StatementPeriod.resolve(from, to, businessZone, clock.now()) }
            .flatMap<StatementError, StatementPeriod, Statement> { period ->
                transactions
                    .findByAccountBetween(accountId, period.from, period.to)
                    .mapError<_, _, StatementError> { StatementError.ServiceUnavailable }
                    .map { Statement(period.from, period.to, it) }
            }
}

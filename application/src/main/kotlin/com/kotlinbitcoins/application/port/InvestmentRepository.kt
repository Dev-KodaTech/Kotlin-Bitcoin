package com.kotlinbitcoins.application.port

import com.kotlinbitcoins.domain.investment.Investment
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Outcome

interface InvestmentRepository {
    /** Persists the investment. Must run inside the caller's transaction so it changes together with the balance. */
    fun save(investment: Investment): Outcome<InfrastructureFailure, Investment>

    /**
     * Records that [investment], already closed in the domain, is no longer active: status `CLOSED` and `closedAt`.
     * Must run inside the caller's transaction. Closing an investment that is not active is a failure.
     */
    fun close(investment: Investment): Outcome<InfrastructureFailure, Investment>

    /** Active investments of the account in FIFO order: oldest `purchasedAt` first, then lowest `fifoSequence`. */
    fun findActiveByAccountInFifoOrder(accountId: AccountId): Outcome<InfrastructureFailure, List<Investment>>

    /** Next value of the sequence that breaks ties between investments bought at the same instant. */
    fun nextFifoSequence(): Outcome<InfrastructureFailure, Long>
}

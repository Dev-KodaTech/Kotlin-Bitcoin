package com.kotlinbitcoins.infrastructure.persistence.investment

import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.InvestmentRepository
import com.kotlinbitcoins.domain.investment.Investment
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.flatMap
import com.kotlinbitcoins.infrastructure.persistence.persistenceCall

/** [InvestmentRepository] over Spring Data JPA. No exception leaves this class (R-03, R-05). */
class InvestmentRepositoryAdapter(
    private val repository: SpringDataInvestmentRepository,
) : InvestmentRepository {
    override fun save(investment: Investment): Outcome<InfrastructureFailure, Investment> =
        persistenceCall("investment.save") {
            // Flush now so constraint violations surface here, not at commit time.
            repository.saveAndFlush(InvestmentMapper.toEntity(investment))
            investment
        }

    override fun close(investment: Investment): Outcome<InfrastructureFailure, Investment> {
        val closedAt =
            investment.closedAt
                ?: return Outcome.Failure(InfrastructureFailure("investment.close failed: investment is not closed"))
        return persistenceCall("investment.close") { repository.closeIfActive(investment.id.value, closedAt) }
            .flatMap { changed ->
                if (changed == 1) {
                    Outcome.Success(investment)
                } else {
                    // Already closed or unknown: the caller's transaction must roll back.
                    Outcome.Failure(InfrastructureFailure("investment.close failed: investment is not active"))
                }
            }
    }

    override fun findActiveByAccountInFifoOrder(
        accountId: AccountId,
    ): Outcome<InfrastructureFailure, List<Investment>> =
        persistenceCall("investment.findActiveByAccountInFifoOrder") {
            repository.findActiveInFifoOrder(accountId.value).map(InvestmentMapper::toDomain)
        }

    override fun nextFifoSequence(): Outcome<InfrastructureFailure, Long> =
        persistenceCall("investment.nextFifoSequence") { repository.nextFifoSequence() }
}

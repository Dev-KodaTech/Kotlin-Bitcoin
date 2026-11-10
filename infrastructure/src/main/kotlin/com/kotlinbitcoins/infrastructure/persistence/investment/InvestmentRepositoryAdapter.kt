package com.kotlinbitcoins.infrastructure.persistence.investment

import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.InvestmentRepository
import com.kotlinbitcoins.domain.investment.Investment
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Outcome
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

    override fun findActiveByAccountInFifoOrder(
        accountId: AccountId,
    ): Outcome<InfrastructureFailure, List<Investment>> =
        persistenceCall("investment.findActiveByAccountInFifoOrder") {
            repository.findActiveInFifoOrder(accountId.value).map(InvestmentMapper::toDomain)
        }

    override fun nextFifoSequence(): Outcome<InfrastructureFailure, Long> =
        persistenceCall("investment.nextFifoSequence") { repository.nextFifoSequence() }
}

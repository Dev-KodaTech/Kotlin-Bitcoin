package com.kotlinbitcoins.infrastructure.persistence.quote

import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.QuoteHistoryRepository
import com.kotlinbitcoins.domain.quote.QuoteSnapshot
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.infrastructure.persistence.persistenceCall
import com.kotlinbitcoins.infrastructure.persistence.quoteFromColumn
import java.time.Instant

/** [QuoteHistoryRepository] over Spring Data JPA. No exception leaves this class (R-03, R-05). */
class QuoteHistoryRepositoryAdapter(
    private val repository: SpringDataQuoteHistoryRepository,
) : QuoteHistoryRepository {
    override fun saveIfAbsent(snapshot: QuoteSnapshot): Outcome<InfrastructureFailure, Boolean> =
        persistenceCall("quoteHistory.saveIfAbsent") {
            repository.insertIfAbsent(
                snapshot.slotAt,
                snapshot.buyPrice.value,
                snapshot.sellPrice.value,
                snapshot.capturedAt,
            ) == 1
        }

    override fun findSince(since: Instant): Outcome<InfrastructureFailure, List<QuoteSnapshot>> =
        persistenceCall("quoteHistory.findSince") {
            repository.findAfter(since).map {
                QuoteSnapshot(
                    slotAt = it.slotAt,
                    buyPrice = quoteFromColumn(it.buyPrice),
                    sellPrice = quoteFromColumn(it.sellPrice),
                    capturedAt = it.capturedAt,
                )
            }
        }

    override fun deleteOlderThan(limit: Instant): Outcome<InfrastructureFailure, Int> =
        persistenceCall("quoteHistory.deleteOlderThan") { repository.deleteBefore(limit) }
}

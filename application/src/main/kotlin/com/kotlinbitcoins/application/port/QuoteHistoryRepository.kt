package com.kotlinbitcoins.application.port

import com.kotlinbitcoins.domain.quote.QuoteSnapshot
import com.kotlinbitcoins.domain.shared.Outcome
import java.time.Instant

/** Store of the quote history, one snapshot per 10 minute slot (FR-027..FR-029). */
interface QuoteHistoryRepository {
    /**
     * Saves [snapshot] unless its slot already has one, in which case the first snapshot stays. Answers `true` when
     * the snapshot was inserted and `false` when the slot was already taken, so a repeated capture is idempotent.
     */
    fun saveIfAbsent(snapshot: QuoteSnapshot): Outcome<InfrastructureFailure, Boolean>

    /** Snapshots with `slotAt > since` (exclusive), oldest first. */
    fun findSince(since: Instant): Outcome<InfrastructureFailure, List<QuoteSnapshot>>

    /** Removes the snapshots with `slotAt < limit` and answers how many were removed. */
    fun deleteOlderThan(limit: Instant): Outcome<InfrastructureFailure, Int>
}

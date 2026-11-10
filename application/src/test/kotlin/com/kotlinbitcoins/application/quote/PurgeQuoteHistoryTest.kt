package com.kotlinbitcoins.application.quote

import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.QuoteHistoryRepository
import com.kotlinbitcoins.domain.shared.Outcome
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class PurgeQuoteHistoryTest {
    private val now = Instant.parse("2026-11-09T06:00:00Z")
    private val history = mockk<QuoteHistoryRepository>()
    private val clock = Clock { now }

    private val purge = PurgeQuoteHistory(history, clock)

    @Test
    fun `removes the snapshots older than 90 days and reports how many`() {
        every { history.deleteOlderThan(any()) } returns Outcome.Success(3)

        val result = purge.execute()

        assertThat(result).isEqualTo(Outcome.Success(3))
        verify(exactly = 1) { history.deleteOlderThan(Instant.parse("2026-08-11T06:00:00Z")) }
    }

    @Test
    fun `an infrastructure failure becomes ServiceUnavailable`() {
        every { history.deleteOlderThan(any()) } returns Outcome.Failure(InfrastructureFailure("database down"))

        val result = purge.execute()

        assertThat(result).isEqualTo(Outcome.Failure(QuoteHistoryError.ServiceUnavailable))
    }
}

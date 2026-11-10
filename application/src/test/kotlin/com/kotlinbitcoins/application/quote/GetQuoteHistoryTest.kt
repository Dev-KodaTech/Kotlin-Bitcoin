package com.kotlinbitcoins.application.quote

import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.QuoteHistoryRepository
import com.kotlinbitcoins.domain.quote.QuoteSnapshot
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class GetQuoteHistoryTest {
    private val now = Instant.parse("2026-11-09T11:17:42Z")
    private val history = mockk<QuoteHistoryRepository>()
    private val clock = Clock { now }

    private val getQuoteHistory = GetQuoteHistory(history, clock)

    @Test
    fun `asks for the points of the last 24 hours`() {
        every { history.findSince(any()) } returns Outcome.Success(emptyList())

        getQuoteHistory.execute()

        verify(exactly = 1) { history.findSince(Instant.parse("2026-11-08T11:17:42Z")) }
    }

    @Test
    fun `returns the points in ascending order`() {
        val first = snapshot("2026-11-09T10:00:00Z")
        val second = snapshot("2026-11-09T10:10:00Z")
        val third = snapshot("2026-11-09T11:10:00Z")
        every { history.findSince(any()) } returns Outcome.Success(listOf(third, first, second))

        val result = getQuoteHistory.execute()

        assertThat((result as Outcome.Success).value).containsExactly(first, second, third)
    }

    @Test
    fun `returns at most 144 points, keeping the most recent ones`() {
        val start = Instant.parse("2026-11-08T11:20:00Z")
        val points = (0 until 150).map { snapshot(start.plus(Duration.ofMinutes(10L * it)).toString()) }
        every { history.findSince(any()) } returns Outcome.Success(points)

        val value = (getQuoteHistory.execute() as Outcome.Success).value

        assertThat(value).hasSize(144)
        assertThat(value.last()).isEqualTo(points.last())
        assertThat(value.first()).isEqualTo(points[6])
    }

    @Test
    fun `without points the answer is an empty list`() {
        every { history.findSince(any()) } returns Outcome.Success(emptyList())

        assertThat(getQuoteHistory.execute()).isEqualTo(Outcome.Success(emptyList<QuoteSnapshot>()))
    }

    @Test
    fun `an infrastructure failure becomes ServiceUnavailable`() {
        every { history.findSince(any()) } returns Outcome.Failure(InfrastructureFailure("database down"))

        assertThat(getQuoteHistory.execute()).isEqualTo(Outcome.Failure(QuoteHistoryError.ServiceUnavailable))
    }

    private fun snapshot(slotAt: String): QuoteSnapshot {
        val slot = Instant.parse(slotAt)
        val price = (QuotePrice.of(BigDecimal("400000.00")) as Outcome.Success).value
        return QuoteSnapshot(slot, price, price, slot.plusSeconds(1))
    }
}

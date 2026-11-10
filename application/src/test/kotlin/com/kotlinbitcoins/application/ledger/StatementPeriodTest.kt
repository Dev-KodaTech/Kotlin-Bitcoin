package com.kotlinbitcoins.application.ledger

import com.kotlinbitcoins.domain.shared.Outcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class StatementPeriodTest {
    private val zone = ZoneId.of("America/Sao_Paulo")

    // 08:10 on 2026-11-09 in São Paulo (UTC-3).
    private val now = Instant.parse("2026-11-09T11:10:00Z")

    @Test
    fun `without dates it goes from 00 00 local of today minus 90 days up to now, now included`() {
        val period = resolve(null, null).success()

        // 2026-11-09 − 90 days = 2026-08-11, midnight in São Paulo.
        assertThat(period.from).isEqualTo(Instant.parse("2026-08-11T03:00:00Z"))
        // The upper bound is exclusive, so it sits one microsecond after now to keep a line stamped exactly now.
        assertThat(period.to).isEqualTo(now.plus(1, ChronoUnit.MICROS))
    }

    @Test
    fun `the default start follows the local date, not the UTC one`() {
        // 01:30 UTC on 2026-11-10 is still 22:30 of 2026-11-09 in São Paulo.
        val lateNight = Instant.parse("2026-11-10T01:30:00Z")

        val period = StatementPeriod.resolve(null, null, zone, lateNight).success()

        assertThat(period.from).isEqualTo(Instant.parse("2026-08-11T03:00:00Z"))
    }

    @Test
    fun `a custom range is inclusive on both ends, in the business zone`() {
        val period = resolve(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-09")).success()

        assertThat(period.from).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"))
        // Exclusive bound: midnight after the end date, so 23:59:59.999… local of 2026-11-09 is inside.
        assertThat(period.to).isEqualTo(Instant.parse("2026-11-10T03:00:00Z"))
    }

    @Test
    fun `from equal to is the whole day`() {
        val day = LocalDate.parse("2026-11-05")

        val period = resolve(day, day).success()

        assertThat(period.from).isEqualTo(Instant.parse("2026-11-05T03:00:00Z"))
        assertThat(period.to).isEqualTo(Instant.parse("2026-11-06T03:00:00Z"))
    }

    @Test
    fun `from after to is InvalidDateRange`() {
        val result = resolve(LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-01"))

        assertThat(result).isEqualTo(Outcome.Failure(StatementError.InvalidDateRange))
    }

    @Test
    fun `only one of the two dates is InvalidDateRange`() {
        assertThat(resolve(LocalDate.parse("2026-11-01"), null))
            .isEqualTo(Outcome.Failure(StatementError.InvalidDateRange))
        assertThat(resolve(null, LocalDate.parse("2026-11-09")))
            .isEqualTo(Outcome.Failure(StatementError.InvalidDateRange))
    }

    private fun resolve(
        from: LocalDate?,
        to: LocalDate?,
    ) = StatementPeriod.resolve(from, to, zone, now)

    private fun <E, T> Outcome<E, T>.success(): T = (this as Outcome.Success).value
}

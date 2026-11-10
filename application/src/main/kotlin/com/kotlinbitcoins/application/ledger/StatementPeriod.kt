package com.kotlinbitcoins.application.ledger

import com.kotlinbitcoins.domain.shared.Outcome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Half-open period `[from, to)` of a statement, as instants (FR-025, FR-034a). The calendar days the client sends are
 * read in the business zone, so a day starts at 00:00:00 local and the end date is inclusive up to 23:59:59.999….
 */
class StatementPeriod private constructor(
    val from: Instant,
    val to: Instant,
) {
    companion object {
        const val DEFAULT_DAYS = 90L

        /**
         * - no dates: from 00:00:00 of (today − 90 days) up to [now]. [now] itself belongs to the period, so the upper
         *   bound sits one microsecond (the database precision) after it;
         * - both dates: from the start of [from] to the start of the day after [to];
         * - one date only, or `from` after `to`: [StatementError.InvalidDateRange].
         */
        fun resolve(
            from: LocalDate?,
            to: LocalDate?,
            zone: ZoneId,
            now: Instant,
        ): Outcome<StatementError.InvalidDateRange, StatementPeriod> =
            when {
                from == null && to == null -> {
                    val today = now.atZone(zone).toLocalDate()
                    Outcome.Success(
                        StatementPeriod(
                            from = today.minusDays(DEFAULT_DAYS).atStartOfDay(zone).toInstant(),
                            to = now.plus(1, ChronoUnit.MICROS),
                        ),
                    )
                }

                from == null || to == null || from.isAfter(to) -> {
                    Outcome.Failure(StatementError.InvalidDateRange)
                }

                else -> {
                    Outcome.Success(
                        StatementPeriod(
                            from = from.atStartOfDay(zone).toInstant(),
                            to = to.plusDays(1).atStartOfDay(zone).toInstant(),
                        ),
                    )
                }
            }
    }
}

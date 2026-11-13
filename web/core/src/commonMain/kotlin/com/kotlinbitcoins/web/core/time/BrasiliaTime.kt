package com.kotlinbitcoins.web.core.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Every date and time shown to the person is in America/Sao_Paulo, whatever the device time zone (FR-037, W-07).
 * On wasmJs the named zone needs `@js-joda/timezone`, referenced by the edge module (:app).
 */
object BrasiliaTime {
    private const val ZONE_ID = "America/Sao_Paulo"

    val zone: TimeZone by lazy { TimeZone.of(ZONE_ID) }

    /** dd/MM/yyyy (declared first: the formats below are built from it). */
    internal val DATE =
        LocalDate.Format {
            day()
            char('/')
            monthNumber()
            char('/')
            year()
        }

    /** dd/MM/yyyy HH:mm */
    internal val DATE_TIME =
        LocalDateTime.Format {
            date(DATE)
            char(' ')
            hour()
            char(':')
            minute()
        }

    /** dd/MM/yyyy HH:mm:ss, used for the quote instant. */
    internal val DATE_TIME_WITH_SECONDS =
        LocalDateTime.Format {
            date(DATE)
            char(' ')
            hour()
            char(':')
            minute()
            char(':')
            second()
        }

    /** The calendar date of [instant] in Brasília, e.g. "today" for the statement suggestions. */
    fun dateOf(instant: Instant): CalendarDate = CalendarDate.of(instant.toLocalDateTime(zone).date)
}

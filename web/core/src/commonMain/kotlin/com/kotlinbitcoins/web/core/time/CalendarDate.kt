package com.kotlinbitcoins.web.core.time

import kotlinx.datetime.LocalDate

/** A calendar date: typed as dd/mm/aaaa, carried as yyyy-MM-dd in the URL and in the API query (W-07). */
class CalendarDate private constructor(
    private val date: LocalDate,
) : Comparable<CalendarDate> {
    /** "2026-10-01", for the URL and the `from`/`to` query of the statement. */
    fun toIso(): String = LocalDate.Formats.ISO.format(date)

    /** "01/10/2026" */
    fun toUserString(): String = BrasiliaTime.DATE.format(date)

    override fun compareTo(other: CalendarDate): Int = date.compareTo(other.date)

    override fun equals(other: Any?): Boolean = other is CalendarDate && other.date == date

    override fun hashCode(): Int = date.hashCode()

    override fun toString(): String = "CalendarDate(${toIso()})"

    companion object {
        private val USER_PATTERN = Regex("""^\d{2}/\d{2}/\d{4}$""")
        private val ISO_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}$""")

        /** "dd/mm/aaaa", two digits for day and month; an impossible date (31/02) is refused. */
        fun parseUserInput(text: String): CalendarDate? {
            val trimmed = text.trim()
            if (!USER_PATTERN.matches(trimmed)) return null
            return BrasiliaTime.DATE.parseOrNull(trimmed)?.let(::CalendarDate)
        }

        /** "yyyy-MM-dd" */
        fun parseIso(text: String): CalendarDate? {
            if (!ISO_PATTERN.matches(text)) return null
            return LocalDate.Formats.ISO
                .parseOrNull(text)
                ?.let(::CalendarDate)
        }

        internal fun of(date: LocalDate): CalendarDate = CalendarDate(date)
    }
}

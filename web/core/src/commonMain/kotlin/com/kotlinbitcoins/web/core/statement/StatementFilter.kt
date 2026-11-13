package com.kotlinbitcoins.web.core.statement

import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.time.CalendarDate

/** Period of the statement (data-model §5): the API default, or both dates with `from ≤ to`. */
sealed interface StatementFilter {
    data object Default : StatementFilter

    data class Range(
        val from: CalendarDate,
        val to: CalendarDate,
    ) : StatementFilter

    companion object {
        /** From the route (`de`/`ate` in yyyy-MM-dd). */
        fun fromIso(
            from: String?,
            to: String?,
        ): Outcome<FilterError, StatementFilter> = build(from, to, CalendarDate::parseIso)

        /** From the form fields (dd/mm/aaaa). Both empty means the default period. */
        fun fromUserInput(
            from: String?,
            to: String?,
        ): Outcome<FilterError, StatementFilter> = build(from, to, CalendarDate::parseUserInput)

        private fun build(
            from: String?,
            to: String?,
            parse: (String) -> CalendarDate?,
        ): Outcome<FilterError, StatementFilter> {
            val fromText = from?.trim().orEmpty()
            val toText = to?.trim().orEmpty()
            if (fromText.isEmpty() && toText.isEmpty()) return Outcome.Success(Default)
            val fromDate = parse(fromText)
            val toDate = parse(toText)
            return if (fromDate != null && toDate != null && fromDate <= toDate) {
                Outcome.Success(Range(fromDate, toDate))
            } else {
                Outcome.Failure(FilterError.InvalidRange)
            }
        }
    }
}

/** Only one date, an invalid date or `from > to`; same message as INVALID_DATE_RANGE (FR-040). */
sealed interface FilterError {
    data object InvalidRange : FilterError
}

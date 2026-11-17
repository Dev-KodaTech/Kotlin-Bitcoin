package com.kotlinbitcoins.web.core.time

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** An instant received from the API (ISO-8601 with offset), always shown in Brasília (FR-037). */
class ApiInstant private constructor(
    val instant: Instant,
) : Comparable<ApiInstant> {
    fun toBrasilia(): LocalDateTime = instant.toLocalDateTime(BrasiliaTime.zone)

    /** "09/11/2026 08:10" */
    fun format(): String = BrasiliaTime.DATE_TIME.format(toBrasilia())

    /** "09/11/2026 08:10:00", for the quote instant. */
    fun formatWithSeconds(): String = BrasiliaTime.DATE_TIME_WITH_SECONDS.format(toBrasilia())

    /** "08:10", for the chart axis and its textual summary (the date is given by the context). */
    fun formatTime(): String = BrasiliaTime.TIME.format(toBrasilia().time)

    override fun compareTo(other: ApiInstant): Int = instant.compareTo(other.instant)

    override fun equals(other: Any?): Boolean = other is ApiInstant && other.instant == instant

    override fun hashCode(): Int = instant.hashCode()

    override fun toString(): String = "ApiInstant($instant)"

    companion object {
        /** Requires date, time and offset (`Z` or `±hh:mm`), as the contract's `Instant`. */
        private val API_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:\d{2})$""")

        fun parseApi(raw: String): Outcome<ApiError, ApiInstant> {
            val instant = if (API_PATTERN.matches(raw)) Instant.parseOrNull(raw) else null
            return instant?.let { Outcome.Success(ApiInstant(it)) } ?: Outcome.Failure(ApiError.Unexpected)
        }

        fun of(instant: Instant): ApiInstant = ApiInstant(instant)
    }
}

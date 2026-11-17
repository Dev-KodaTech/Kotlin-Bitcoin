package com.kotlinbitcoins.web.core.statement

import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.time.CalendarDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StatementFilterTest {
    private val invalid = Outcome.Failure(FilterError.InvalidRange)

    private fun date(iso: String): CalendarDate = requireNotNull(CalendarDate.parseIso(iso))

    @Test
    fun `both dates empty is the default period`() {
        assertEquals(Outcome.Success(StatementFilter.Default), StatementFilter.fromUserInput("", " "))
        assertEquals(Outcome.Success(StatementFilter.Default), StatementFilter.fromIso(null, null))
    }

    @Test
    fun `both dates are required together`() {
        assertEquals(invalid, StatementFilter.fromUserInput("01/10/2026", ""))
        assertEquals(invalid, StatementFilter.fromUserInput("", "09/11/2026"))
        assertEquals(invalid, StatementFilter.fromIso("2026-10-01", null))
        assertEquals(invalid, StatementFilter.fromIso(null, "2026-11-09"))
    }

    @Test
    fun `invalid dates are an invalid range`() {
        assertEquals(invalid, StatementFilter.fromUserInput("31/02/2026", "09/11/2026"))
        assertEquals(invalid, StatementFilter.fromUserInput("1/10/2026", "09/11/2026"))
        assertEquals(invalid, StatementFilter.fromUserInput("2026-10-01", "09/11/2026"))
        assertEquals(invalid, StatementFilter.fromIso("2026-13-01", "2026-11-09"))
        assertEquals(invalid, StatementFilter.fromIso("01/10/2026", "2026-11-09"))
    }

    @Test
    fun `from after to is an invalid range`() {
        assertEquals(invalid, StatementFilter.fromUserInput("10/11/2026", "01/11/2026"))
        assertEquals(invalid, StatementFilter.fromIso("2026-11-10", "2026-11-01"))
    }

    @Test
    fun `a valid range, including from equal to to`() {
        assertEquals(
            Outcome.Success(StatementFilter.Range(date("2026-10-01"), date("2026-11-09"))),
            StatementFilter.fromUserInput("01/10/2026", "09/11/2026"),
        )
        assertEquals(
            Outcome.Success(StatementFilter.Range(date("2026-11-09"), date("2026-11-09"))),
            StatementFilter.fromUserInput("09/11/2026", "09/11/2026"),
        )
        assertEquals(
            Outcome.Success(StatementFilter.Range(date("2026-11-09"), date("2026-11-09"))),
            StatementFilter.fromIso("2026-11-09", "2026-11-09"),
        )
    }
}

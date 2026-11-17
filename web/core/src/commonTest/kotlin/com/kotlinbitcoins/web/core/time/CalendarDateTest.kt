package com.kotlinbitcoins.web.core.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarDateTest {
    @Test
    fun `user input becomes ISO`() {
        assertEquals("2026-10-01", CalendarDate.parseUserInput("01/10/2026")?.toIso())
        assertEquals("2024-02-29", CalendarDate.parseUserInput(" 29/02/2024 ")?.toIso())
    }

    @Test
    fun `invalid user input is refused`() {
        listOf("31/02/2026", "1/10/2026", "2026-10-01", "", "01/13/2026", "01/10/26", "aa/bb/cccc").forEach { raw ->
            assertNull(CalendarDate.parseUserInput(raw), "\"$raw\"")
        }
    }

    @Test
    fun `ISO round trip`() {
        val date = CalendarDate.parseIso("2026-11-09")
        assertEquals("2026-11-09", date?.toIso())
        assertEquals("09/11/2026", date?.toUserString())
        assertNull(CalendarDate.parseIso("09/11/2026"))
        assertNull(CalendarDate.parseIso("2026-02-30"))
        assertNull(CalendarDate.parseIso("2026-1-9"))
    }

    @Test
    fun `dates are ordered`() {
        val first = CalendarDate.parseIso("2026-10-01")!!
        val second = CalendarDate.parseIso("2026-11-09")!!
        assertTrue(first < second)
    }
}

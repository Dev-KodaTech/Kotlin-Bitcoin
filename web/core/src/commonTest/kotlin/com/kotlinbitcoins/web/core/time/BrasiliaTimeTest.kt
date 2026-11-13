package com.kotlinbitcoins.web.core.time

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.support.failureError
import com.kotlinbitcoins.web.core.support.successValue
import kotlin.test.Test
import kotlin.test.assertEquals

class BrasiliaTimeTest {
    private fun instant(raw: String): ApiInstant = ApiInstant.parseApi(raw).successValue()

    @Test
    fun `instant with Brasilia offset`() {
        assertEquals("09/11/2026 08:10", instant("2026-11-09T08:10:00-03:00").format())
    }

    @Test
    fun `instant with seconds`() {
        assertEquals("09/11/2026 08:10:00", instant("2026-11-09T08:10:00-03:00").formatWithSeconds())
        assertEquals("09/11/2026 08:10:07", instant("2026-11-09T08:10:07.5-03:00").formatWithSeconds())
    }

    @Test
    fun `UTC instant is shown in Brasilia regardless of the device time zone`() {
        assertEquals("09/11/2026 08:10", instant("2026-11-09T11:10:00Z").format())
        assertEquals("31/12/2026 21:30", instant("2027-01-01T00:30:00Z").format())
    }

    @Test
    fun `invalid instant fails`() {
        listOf("", "abc", "2026-13-09T08:10:00Z", "2026-11-09", "09/11/2026 08:10").forEach { raw ->
            assertEquals(ApiError.Unexpected, ApiInstant.parseApi(raw).failureError(), "\"$raw\"")
        }
    }

    @Test
    fun `instants are ordered`() {
        assertEquals(
            0,
            instant("2026-11-09T08:10:00-03:00").compareTo(instant("2026-11-09T11:10:00Z")),
        )
    }
}

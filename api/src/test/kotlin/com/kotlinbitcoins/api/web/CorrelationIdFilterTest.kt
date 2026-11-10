package com.kotlinbitcoins.api.web

import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID

class CorrelationIdFilterTest {
    private val filter = CorrelationIdFilter()

    @AfterEach
    fun clearMdc() = MDC.clear()

    @Test
    fun `reuses the received correlation id and puts it in the MDC during the request`() {
        val request = MockHttpServletRequest().apply { addHeader(CorrelationIdFilter.HEADER, "abc-123") }
        val response = MockHttpServletResponse()
        var seenInMdc: String? = null

        filter.doFilter(request, response, FilterChain { _, _ -> seenInMdc = MDC.get(CorrelationIdFilter.MDC_KEY) })

        assertThat(seenInMdc).isEqualTo("abc-123")
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123")
    }

    @Test
    fun `generates a UUID when the header is missing`() {
        val response = MockHttpServletResponse()
        var seenInMdc: String? = null

        filter.doFilter(
            MockHttpServletRequest(),
            response,
            FilterChain { _, _ -> seenInMdc = MDC.get(CorrelationIdFilter.MDC_KEY) },
        )

        val generated = response.getHeader(CorrelationIdFilter.HEADER)
        assertThat(generated).isNotBlank()
        assertThat(UUID.fromString(generated).toString()).isEqualTo(generated)
        assertThat(seenInMdc).isEqualTo(generated)
    }

    @Test
    fun `replaces a header value that is unsafe to log`() {
        val request = MockHttpServletRequest().apply { addHeader(CorrelationIdFilter.HEADER, "bad\nvalue") }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ -> })

        val used = response.getHeader(CorrelationIdFilter.HEADER)
        assertThat(used).isNotEqualTo("bad\nvalue")
        assertThat(UUID.fromString(used).toString()).isEqualTo(used)
    }

    @Test
    fun `clears the MDC after the request, even when the chain fails`() {
        val request = MockHttpServletRequest().apply { addHeader(CorrelationIdFilter.HEADER, "abc-123") }

        runCatching {
            filter.doFilter(request, MockHttpServletResponse(), FilterChain { _, _ -> error("chain failed") })
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull()
    }
}

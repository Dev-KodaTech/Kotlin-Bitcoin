package com.kotlinbitcoins.api.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Reuses the received `X-Correlation-Id` (or generates a UUID), exposes it in the MDC for the whole request and
 * returns it in the response (R-11). Runs before Spring Security so 401 responses carry it too.
 * Values that are unsafe to log (control characters, too long) are replaced by a new id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId =
            request.getHeader(HEADER)?.takeIf { SAFE_VALUE.matches(it) } ?: UUID.randomUUID().toString()
        MDC.put(MDC_KEY, correlationId)
        response.setHeader(HEADER, correlationId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        const val HEADER = "X-Correlation-Id"
        const val MDC_KEY = "correlationId"

        private val SAFE_VALUE = Regex("^[A-Za-z0-9._-]{1,64}$")
    }
}

package com.kotlinbitcoins.api.error

import com.kotlinbitcoins.api.web.CorrelationIdFilter
import com.kotlinbitcoins.application.port.InfrastructureUnavailable
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.net.URI

/** A single invalid input field, listed in `errors` of a `VALIDATION_ERROR`. */
data class FieldViolation(
    val field: String,
    val message: String,
)

/** Builds RFC 9457 responses (`application/problem+json`) with a stable `code` and the request's correlation id. */
@Component
class ProblemDetailsMapper {
    fun problem(
        code: ProblemCode,
        detail: String = code.defaultDetail,
        errors: List<FieldViolation> = emptyList(),
        status: Int = code.status.value(),
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail).apply {
            type = ABOUT_BLANK
            title = code.title
            setProperty("code", code.name)
            MDC.get(CorrelationIdFilter.MDC_KEY)?.let { setProperty("correlationId", it) }
            if (errors.isNotEmpty()) setProperty("errors", errors)
        }

    fun response(
        code: ProblemCode,
        detail: String = code.defaultDetail,
        errors: List<FieldViolation> = emptyList(),
        status: Int = code.status.value(),
        headers: HttpHeaders = HttpHeaders.EMPTY,
    ): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(status)
            .headers(headers)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem(code, detail, errors, status))

    /** Any operation error tagged as [InfrastructureUnavailable] becomes `SERVICE_UNAVAILABLE` (503). */
    @Suppress("UnusedParameter")
    fun serviceUnavailable(error: InfrastructureUnavailable): ResponseEntity<ProblemDetail> =
        response(ProblemCode.SERVICE_UNAVAILABLE)

    companion object {
        private val ABOUT_BLANK = URI.create("about:blank")
    }
}

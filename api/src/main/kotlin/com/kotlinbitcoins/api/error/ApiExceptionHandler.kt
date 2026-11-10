package com.kotlinbitcoins.api.error

import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

/**
 * Handles only framework errors (malformed body, Bean Validation, unknown routes, unexpected failures).
 * Business errors never arrive here: they are values mapped by each controller through [ProblemDetailsMapper].
 */
@RestControllerAdvice
class ApiExceptionHandler(
    private val problems: ProblemDetailsMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(HttpMessageNotReadableException::class, TypeMismatchException::class)
    fun unreadableInput(): ResponseEntity<ProblemDetail> = problems.response(ProblemCode.VALIDATION_ERROR)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(exception: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val errors =
            exception.bindingResult.fieldErrors.map {
                FieldViolation(it.field, it.defaultMessage ?: INVALID_VALUE)
            }
        return problems.response(ProblemCode.VALIDATION_ERROR, errors = errors)
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun invalidParameters(exception: HandlerMethodValidationException): ResponseEntity<ProblemDetail> {
        val errors =
            exception.parameterValidationResults.flatMap { result ->
                result.resolvableErrors.map {
                    FieldViolation(result.methodParameter.parameterName ?: "", it.defaultMessage ?: INVALID_VALUE)
                }
            }
        return problems.response(ProblemCode.VALIDATION_ERROR, errors = errors)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun unauthenticated(): ResponseEntity<ProblemDetail> = problems.response(ProblemCode.UNAUTHORIZED)

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception): ResponseEntity<ProblemDetail> {
        if (exception is ErrorResponse && exception.statusCode.is4xxClientError) {
            return frameworkClientError(exception)
        }
        logger.error("Unexpected error while handling the request", exception)
        return problems.response(ProblemCode.INTERNAL_ERROR)
    }

    /** Spring MVC errors such as unknown route (404), wrong method (405) or media type (415) keep their status. */
    private fun frameworkClientError(exception: ErrorResponse): ResponseEntity<ProblemDetail> {
        val status = exception.statusCode.value()
        return if (status == HttpStatus.NOT_FOUND.value()) {
            problems.response(ProblemCode.NOT_FOUND)
        } else {
            problems.response(ProblemCode.VALIDATION_ERROR, status = status, headers = exception.headers)
        }
    }

    private companion object {
        const val INVALID_VALUE = "Valor inválido."
    }
}

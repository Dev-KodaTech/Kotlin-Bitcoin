package com.kotlinbitcoins.api.openapi

import io.swagger.v3.oas.annotations.media.Schema

/** `Content-Type` of every error response (RFC 9457). */
const val PROBLEM_JSON = "application/problem+json"

/**
 * Documentation-only model of the Problem Details body, referenced by the `@ApiResponse` annotations of the
 * controllers. The real body is built by `ProblemDetailsMapper`; this class only describes it in the OpenAPI document.
 */
@Schema(name = "Problem", description = "RFC 9457 Problem Details")
data class ProblemBody(
    val type: String,
    val title: String,
    val status: Int,
    @field:Schema(description = "Código estável de erro (R-03).")
    val code: String,
    @field:Schema(description = "Mensagem em português, sem detalhes internos.")
    val detail: String,
    val instance: String? = null,
    val correlationId: String? = null,
    @field:Schema(description = "Só em VALIDATION_ERROR.")
    val errors: List<FieldErrorBody>? = null,
)

@Schema(name = "FieldError")
data class FieldErrorBody(
    val field: String,
    val message: String,
)

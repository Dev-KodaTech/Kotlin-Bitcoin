package com.kotlinbitcoins.api.error

import org.springframework.http.HttpStatus

/**
 * Stable error codes of the API (R-03), each with its HTTP status, title and default Portuguese detail.
 * The `code` field is what clients branch on; titles follow the contract (contracts/openapi.yaml).
 */
enum class ProblemCode(
    val status: HttpStatus,
    val title: String,
    val defaultDetail: String,
) {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Bad Request", "Dados de entrada inválidos."),
    INVALID_AMOUNT(
        HttpStatus.BAD_REQUEST,
        "Bad Request",
        "O valor deve ser positivo e ter no máximo 2 casas decimais.",
    ),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Unauthorized", "E-mail ou senha inválidos."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized", "Autenticação necessária."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not Found", "Recurso não encontrado."),
    EMAIL_ALREADY_IN_USE(HttpStatus.CONFLICT, "Conflict", "Este e-mail já está em uso."),
    INSUFFICIENT_BALANCE(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Unprocessable Content",
        "Saldo insuficiente para esta compra.",
    ),
    REDEMPTION_EXCEEDS_AVAILABLE(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Unprocessable Content",
        "O valor solicitado excede o valor atual dos seus investimentos.",
    ),
    INVALID_DATE_RANGE(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Content", "Intervalo de datas inválido."),
    INTERNAL_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal Server Error",
        "Erro interno. Tente novamente mais tarde.",
    ),
    QUOTE_UNAVAILABLE(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Service Unavailable",
        "Cotação indisponível no momento. Tente novamente em instantes.",
    ),
    SERVICE_UNAVAILABLE(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Service Unavailable",
        "Serviço temporariamente indisponível. Tente novamente em instantes.",
    ),
}

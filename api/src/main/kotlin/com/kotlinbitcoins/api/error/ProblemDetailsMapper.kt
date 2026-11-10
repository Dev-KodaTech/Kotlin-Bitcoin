package com.kotlinbitcoins.api.error

import com.kotlinbitcoins.api.web.CorrelationIdFilter
import com.kotlinbitcoins.application.account.DepositError
import com.kotlinbitcoins.application.account.FieldError
import com.kotlinbitcoins.application.account.GetBalanceError
import com.kotlinbitcoins.application.account.LoginError
import com.kotlinbitcoins.application.account.RegisterAccountError
import com.kotlinbitcoins.application.account.Violation
import com.kotlinbitcoins.application.investment.GetInvestmentPositionError
import com.kotlinbitcoins.application.investment.PurchaseError
import com.kotlinbitcoins.application.investment.RedemptionError
import com.kotlinbitcoins.application.ledger.DailyVolumeError
import com.kotlinbitcoins.application.ledger.StatementError
import com.kotlinbitcoins.application.port.InfrastructureUnavailable
import com.kotlinbitcoins.application.quote.QuoteError
import com.kotlinbitcoins.application.quote.QuoteHistoryError
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
// One mapping function per operation error, so the count grows with the API, not with complexity.
@Suppress("TooManyFunctions")
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

    fun registerAccountError(error: RegisterAccountError): ResponseEntity<ProblemDetail> =
        when (error) {
            is RegisterAccountError.InvalidInput -> {
                val errors = error.fields.map { FieldViolation(it.field, it.message()) }
                response(ProblemCode.VALIDATION_ERROR, errors = errors)
            }

            RegisterAccountError.EmailAlreadyInUse -> {
                response(ProblemCode.EMAIL_ALREADY_IN_USE)
            }

            is RegisterAccountError.ServiceUnavailable -> {
                serviceUnavailable(error)
            }
        }

    fun loginError(error: LoginError): ResponseEntity<ProblemDetail> =
        when (error) {
            LoginError.InvalidCredentials -> response(ProblemCode.INVALID_CREDENTIALS)
            is LoginError.ServiceUnavailable -> serviceUnavailable(error)
        }

    /** Amount that is not positive, not numeric or has more than 2 decimals (FR-008, R-10). */
    fun invalidAmount(): ResponseEntity<ProblemDetail> = response(ProblemCode.INVALID_AMOUNT)

    fun depositError(error: DepositError): ResponseEntity<ProblemDetail> =
        when (error) {
            DepositError.AccountNotFound -> response(ProblemCode.UNAUTHORIZED)
            is DepositError.ServiceUnavailable -> serviceUnavailable(error)
        }

    fun getBalanceError(error: GetBalanceError): ResponseEntity<ProblemDetail> =
        when (error) {
            GetBalanceError.AccountNotFound -> response(ProblemCode.UNAUTHORIZED)
            is GetBalanceError.ServiceUnavailable -> serviceUnavailable(error)
        }

    fun purchaseError(error: PurchaseError): ResponseEntity<ProblemDetail> =
        when (error) {
            PurchaseError.InvalidAmount -> invalidAmount()
            PurchaseError.InsufficientBalance -> response(ProblemCode.INSUFFICIENT_BALANCE)
            PurchaseError.QuoteUnavailable -> response(ProblemCode.QUOTE_UNAVAILABLE)
            PurchaseError.AccountNotFound -> response(ProblemCode.UNAUTHORIZED)
            is PurchaseError.ServiceUnavailable -> serviceUnavailable(error)
        }

    fun redemptionError(error: RedemptionError): ResponseEntity<ProblemDetail> =
        when (error) {
            RedemptionError.RedemptionExceedsAvailable -> response(ProblemCode.REDEMPTION_EXCEEDS_AVAILABLE)
            RedemptionError.QuoteUnavailable -> response(ProblemCode.QUOTE_UNAVAILABLE)
            RedemptionError.AccountNotFound -> response(ProblemCode.UNAUTHORIZED)
            is RedemptionError.ServiceUnavailable -> serviceUnavailable(error)
        }

    fun getInvestmentPositionError(error: GetInvestmentPositionError): ResponseEntity<ProblemDetail> =
        when (error) {
            GetInvestmentPositionError.AccountNotFound -> response(ProblemCode.UNAUTHORIZED)
            GetInvestmentPositionError.QuoteUnavailable -> response(ProblemCode.QUOTE_UNAVAILABLE)
            is GetInvestmentPositionError.ServiceUnavailable -> serviceUnavailable(error)
        }

    fun statementError(error: StatementError): ResponseEntity<ProblemDetail> =
        when (error) {
            StatementError.AccountNotFound -> response(ProblemCode.UNAUTHORIZED)
            StatementError.InvalidDateRange -> response(ProblemCode.INVALID_DATE_RANGE)
            is StatementError.ServiceUnavailable -> serviceUnavailable(error)
        }

    fun dailyVolumeError(error: DailyVolumeError): ResponseEntity<ProblemDetail> =
        when (error) {
            is DailyVolumeError.ServiceUnavailable -> serviceUnavailable(error)
        }

    fun quoteError(error: QuoteError): ResponseEntity<ProblemDetail> =
        when (error) {
            QuoteError.QuoteUnavailable -> response(ProblemCode.QUOTE_UNAVAILABLE)
        }

    fun quoteHistoryError(error: QuoteHistoryError): ResponseEntity<ProblemDetail> =
        when (error) {
            QuoteHistoryError.SourceUnavailable -> response(ProblemCode.QUOTE_UNAVAILABLE)
            is QuoteHistoryError.ServiceUnavailable -> serviceUnavailable(error)
        }

    private fun FieldError.message(): String =
        FIELD_MESSAGES[field to violation] ?: GENERIC_MESSAGES.getValue(violation)

    companion object {
        private val ABOUT_BLANK = URI.create("about:blank")

        private val FIELD_MESSAGES: Map<Pair<String, Violation>, String> =
            mapOf(
                ("name" to Violation.REQUIRED) to "Nome é obrigatório.",
                ("name" to Violation.TOO_LONG) to "Nome deve ter no máximo 200 caracteres.",
                ("email" to Violation.REQUIRED) to "E-mail é obrigatório.",
                ("email" to Violation.INVALID_FORMAT) to "E-mail em formato inválido.",
                ("password" to Violation.REQUIRED) to "Senha é obrigatória.",
                ("password" to Violation.TOO_SHORT) to "A senha deve ter no mínimo 8 caracteres.",
                ("password" to Violation.TOO_LONG) to "A senha deve ter no máximo 72 bytes.",
            )

        private val GENERIC_MESSAGES: Map<Violation, String> =
            mapOf(
                Violation.REQUIRED to "Campo obrigatório.",
                Violation.INVALID_FORMAT to "Formato inválido.",
                Violation.TOO_SHORT to "Valor abaixo do tamanho mínimo.",
                Violation.TOO_LONG to "Valor acima do tamanho máximo.",
            )
    }
}

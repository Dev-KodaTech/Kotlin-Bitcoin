package com.kotlinbitcoins.api.ledger

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.api.web.CurrentAccount
import com.kotlinbitcoins.application.ledger.GetStatement
import com.kotlinbitcoins.application.ledger.StatementError
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.fold
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Statement of the authenticated account, newest line first (US8, FR-023..FR-025). */
@RestController
class StatementController(
    private val getStatement: GetStatement,
    private val formats: ApiFormats,
    private val problems: ProblemDetailsMapper,
) {
    /**
     * The dates arrive as text so that a malformed one is an `INVALID_DATE_RANGE` (422) and not Spring's generic
     * type-mismatch `400`.
     */
    @GetMapping("/api/v1/me/statement")
    fun statement(
        @CurrentAccount accountId: AccountId,
        @RequestParam(name = "from", required = false) from: String?,
        @RequestParam(name = "to", required = false) to: String?,
    ): ResponseEntity<*> {
        if (isMalformed(from) || isMalformed(to)) {
            return problems.statementError(StatementError.InvalidDateRange)
        }
        return getStatement
            .execute(accountId, from?.let(::parseDate), to?.let(::parseDate))
            .fold(
                onFailure = problems::statementError,
                onSuccess = { ResponseEntity.ok(StatementResponse.of(it, formats)) },
            )
    }

    private fun isMalformed(text: String?): Boolean = text != null && parseDate(text) == null

    /** Strict ISO `yyyy-MM-dd`: `2026-13-01` and `2026-02-30` do not parse. Null when it is not a valid date. */
    private fun parseDate(text: String): LocalDate? =
        try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
}

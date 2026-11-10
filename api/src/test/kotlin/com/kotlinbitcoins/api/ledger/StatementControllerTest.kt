package com.kotlinbitcoins.api.ledger

import com.kotlinbitcoins.api.support.WebMvcTestConfiguration
import com.kotlinbitcoins.application.ledger.GetStatement
import com.kotlinbitcoins.application.ledger.Statement
import com.kotlinbitcoins.application.ledger.StatementError
import com.kotlinbitcoins.domain.ledger.Transaction
import com.kotlinbitcoins.domain.ledger.TransactionType
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvestmentId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import com.kotlinbitcoins.domain.shared.RedemptionRequestId
import com.kotlinbitcoins.domain.shared.TransactionId
import com.ninjasquad.springmockk.MockkBean
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(controllers = [StatementController::class])
@Import(WebMvcTestConfiguration::class)
class StatementControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var getStatement: GetStatement

    private val accountId = UUID.fromString("7f8a1c2e-1111-4222-8333-944455556666")
    private val from = Instant.parse("2026-08-11T03:00:00Z")
    private val to = Instant.parse("2026-11-09T11:10:00Z")

    @Test
    fun `answers 200 with the period and the entries, a deposit without bitcoin data`() {
        val deposit = line(TransactionType.DEPOSIT, "1000.00", null, null, "2026-11-09T10:00:00Z")
        every { getStatement.execute(AccountId(accountId), null, null) } returns
            Outcome.Success(Statement(from, to, listOf(deposit)))

        statement()
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.from") { value("2026-08-11T00:00:00-03:00") }
                jsonPath("$.to") { value("2026-11-09T08:10:00-03:00") }
                jsonPath("$.entries.length()") { value(1) }
                jsonPath("$.entries[0].id") { value(deposit.id.toString()) }
                jsonPath("$.entries[0].type") { value("DEPOSIT") }
                jsonPath("$.entries[0].amount") { value("1000.00") }
                jsonPath("$.entries[0].btcAmount") { value(null) }
                jsonPath("$.entries[0].quote") { value(null) }
                jsonPath("$.entries[0].investmentId") { value(null) }
                jsonPath("$.entries[0].requestId") { value(null) }
                jsonPath("$.entries[0].occurredAt") { value("2026-11-09T07:00:00-03:00") }
            }
    }

    @Test
    fun `purchase and redemption lines carry the bitcoin amount, the quote and the ids`() {
        val investmentId = InvestmentId.random()
        val requestId = RedemptionRequestId.random()
        val purchase =
            line(TransactionType.PURCHASE, "400.00", 100_000, "400000.00", "2026-11-02T10:00:00Z", investmentId)
        val redemption =
            line(
                TransactionType.REDEMPTION,
                "200.00",
                100_000,
                "200000.00",
                "2026-11-05T10:00:00Z",
                investmentId,
                requestId,
            )
        every { getStatement.execute(AccountId(accountId), null, null) } returns
            Outcome.Success(Statement(from, to, listOf(redemption, purchase)))

        statement().andExpect {
            status { isOk() }
            jsonPath("$.entries[0].type") { value("REDEMPTION") }
            jsonPath("$.entries[0].btcAmount") { value("0.00100000") }
            jsonPath("$.entries[0].quote") { value("200000.00") }
            jsonPath("$.entries[0].investmentId") { value(investmentId.toString()) }
            jsonPath("$.entries[0].requestId") { value(requestId.toString()) }
            jsonPath("$.entries[1].type") { value("PURCHASE") }
            jsonPath("$.entries[1].requestId") { value(null) }
        }
    }

    @Test
    fun `passes the dates to the use case`() {
        every { getStatement.execute(AccountId(accountId), any(), any()) } returns
            Outcome.Success(Statement(from, to, emptyList()))

        statement("?from=2026-10-01&to=2026-11-09").andExpect {
            status { isOk() }
            jsonPath("$.entries.length()") { value(0) }
        }

        verify {
            getStatement.execute(AccountId(accountId), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-09"))
        }
    }

    @Test
    fun `an invalid range from the use case is 422 INVALID_DATE_RANGE`() {
        every { getStatement.execute(AccountId(accountId), any(), any()) } returns
            Outcome.Failure(StatementError.InvalidDateRange)

        statement("?from=2026-11-10&to=2026-11-01").andExpect {
            status { isUnprocessableEntity() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("INVALID_DATE_RANGE") }
            jsonPath("$.detail") { value("Intervalo de datas inválido.") }
        }
    }

    @Test
    fun `a malformed date is 422 INVALID_DATE_RANGE, not 400, and never reaches the use case`() {
        listOf("?from=2026-13-01&to=2026-11-09", "?from=abc&to=2026-11-09", "?from=2026-10-01&to=2026-02-30")
            .forEach { query ->
                statement(query).andExpect {
                    status { isUnprocessableEntity() }
                    jsonPath("$.code") { value("INVALID_DATE_RANGE") }
                }
            }

        confirmVerified(getStatement)
    }

    @Test
    fun `an infrastructure failure is 503 SERVICE_UNAVAILABLE`() {
        every { getStatement.execute(AccountId(accountId), null, null) } returns
            Outcome.Failure(StatementError.ServiceUnavailable)

        statement().andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("SERVICE_UNAVAILABLE") }
        }
    }

    @Test
    fun `without a token it is 401 and never reaches the use case`() {
        mockMvc.get("/api/v1/me/statement").andExpect { status { isUnauthorized() } }

        confirmVerified(getStatement)
    }

    private fun statement(query: String = ""): ResultActionsDsl =
        mockMvc.get("/api/v1/me/statement$query") {
            with(jwt().jwt { it.subject(accountId.toString()) })
        }

    @Suppress("LongParameterList")
    private fun line(
        type: TransactionType,
        amount: String,
        sats: Long?,
        quote: String?,
        at: String,
        investmentId: InvestmentId? = null,
        requestId: RedemptionRequestId? = null,
    ) = Transaction(
        id = TransactionId.random(),
        accountId = AccountId(accountId),
        type = type,
        amount = (Money.of(BigDecimal(amount)) as Outcome.Success).value,
        btcAmount = sats?.let(::BtcAmount),
        quote = quote?.let { (QuotePrice.of(BigDecimal(it)) as Outcome.Success).value },
        investmentId = investmentId,
        requestId = requestId,
        occurredAt = Instant.parse(at),
    )
}

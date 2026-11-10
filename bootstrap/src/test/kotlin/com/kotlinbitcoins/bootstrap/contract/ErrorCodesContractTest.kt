package com.kotlinbitcoins.bootstrap.contract

import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.util.UUID

/**
 * Each of the 10 stable error codes of R-03, provoked through the real API (FR-035, SC-010). Every response must be
 * `application/problem+json` with the contract's status, a `code`, a Portuguese `detail` and no internal detail.
 * `SERVICE_UNAVAILABLE` is provoked by pausing the PostgreSQL container.
 */
class ErrorCodesContractTest : ApplicationTestBase() {
    @Test
    fun `VALIDATION_ERROR - malformed body and missing fields are 400 with the invalid fields`() {
        post("/api/v1/accounts", """{"name":"","email":"","password":""}""")
            .andExpect { jsonPath("$.errors") { isNotEmpty() } }
            .isProblem(400, "VALIDATION_ERROR", "Dados de entrada inválidos.")
        post("/api/v1/auth/login", "{ not json").isProblem(400, "VALIDATION_ERROR", "Dados de entrada inválidos.")
    }

    @Test
    fun `INVALID_AMOUNT - zero, negative, text and too many decimals are 400`() {
        val ana = api.register("Ana", "ana@example.com")

        listOf("0", "-5", "abc", "10.123").forEach { amount ->
            api.deposit(ana, amount).isProblem(400, "INVALID_AMOUNT", AMOUNT_DETAIL)
        }
        assertThat(api.balance(ana)).isEqualTo("0.00")
    }

    @Test
    fun `INVALID_CREDENTIALS - wrong password and unknown e-mail are the same 401`() {
        api.register("Ana", "ana@example.com")

        api
            .login(
                "ana@example.com",
                "senha-errada-123",
            ).isProblem(401, "INVALID_CREDENTIALS", "E-mail ou senha inválidos.")
        api.login("ninguem@example.com").isProblem(401, "INVALID_CREDENTIALS", "E-mail ou senha inválidos.")
    }

    @Test
    fun `UNAUTHORIZED - no token, malformed token and token of an unknown account are 401`() {
        mockMvc.get("/api/v1/me/balance").isProblem(401, "UNAUTHORIZED", "Autenticação necessária.")
        mockMvc
            .get("/api/v1/me/balance") { header(HttpHeaders.AUTHORIZATION, "Bearer abc") }
            .isProblem(401, "UNAUTHORIZED", "Autenticação necessária.")
        api.get("/api/v1/me/balance", UUID.randomUUID()).isProblem(401, "UNAUTHORIZED", "Autenticação necessária.")
    }

    @Test
    fun `EMAIL_ALREADY_IN_USE - the same e-mail twice is 409`() {
        api.register("Ana", "ana@example.com")

        post("/api/v1/accounts", """{"name":"Outra Ana","email":"ANA@example.com ","password":"s3nha-forte"}""")
            .isProblem(409, "EMAIL_ALREADY_IN_USE", "Este e-mail já está em uso.")
    }

    @Test
    fun `INSUFFICIENT_BALANCE - a purchase above the balance is 422`() {
        stubQuote(buy = "400000.00", sell = "400000.00")
        val ana = api.register("Ana", "ana@example.com")
        api.depositOk(ana, "100.00")

        api.purchase(ana, "100.01").isProblem(422, "INSUFFICIENT_BALANCE", "Saldo insuficiente para esta compra.")
    }

    @Test
    fun `REDEMPTION_EXCEEDS_AVAILABLE - a redemption above the value of the investments is 422`() {
        stubQuote(buy = "400000.00", sell = "400000.00")
        val ana = api.register("Ana", "ana@example.com")
        api.depositOk(ana, "100.00")
        api.purchaseOk(ana, "100.00")

        api
            .redeem(ana, "100.01")
            .isProblem(
                422,
                "REDEMPTION_EXCEEDS_AVAILABLE",
                "O valor solicitado excede o valor atual dos seus investimentos.",
            )
    }

    @Test
    fun `INVALID_DATE_RANGE - from after to and a lone date are 422`() {
        val ana = api.register("Ana", "ana@example.com")

        api
            .get("/api/v1/me/statement?from=2026-11-10&to=2026-11-01", ana)
            .isProblem(422, "INVALID_DATE_RANGE", "Intervalo de datas inválido.")
        api
            .get(
                "/api/v1/me/statement?from=2026-11-01",
                ana,
            ).isProblem(422, "INVALID_DATE_RANGE", "Intervalo de datas inválido.")
    }

    @Test
    fun `QUOTE_UNAVAILABLE - with the source down and nothing fresh in the cache the quote is 503`() {
        val ana = api.register("Ana", "ana@example.com")
        quoteSourceDown()

        api
            .get("/api/v1/quotes/current", ana)
            .isProblem(503, "QUOTE_UNAVAILABLE", "Cotação indisponível no momento. Tente novamente em instantes.")
    }

    @Test
    fun `SERVICE_UNAVAILABLE - with the database paused a protected route is 503 and recovers afterwards`() {
        val ana = api.register("Ana", "ana@example.com")
        val token = api.bearer(ana)

        postgres.dockerClient.pauseContainerCmd(postgres.containerId).exec()
        try {
            mockMvc
                .get("/api/v1/me/balance") { header(HttpHeaders.AUTHORIZATION, token) }
                .isProblem(
                    503,
                    "SERVICE_UNAVAILABLE",
                    "Serviço temporariamente indisponível. Tente novamente em instantes.",
                )
        } finally {
            postgres.dockerClient.unpauseContainerCmd(postgres.containerId).exec()
        }

        // The pool must recover by itself (readiness) so the next tests are not affected.
        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted {
            assertThat(api.balance(ana)).isEqualTo("0.00")
        }
    }

    private fun post(
        path: String,
        body: String,
    ): ResultActionsDsl =
        mockMvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun ResultActionsDsl.isProblem(
        status: Int,
        code: String,
        detail: String,
    ): ResultActionsDsl =
        andExpect {
            status { isEqualTo(status) }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("about:blank") }
            jsonPath("$.status") { value(status) }
            jsonPath("$.code") { value(code) }
            jsonPath("$.detail") { value(detail) }
        }.also { result ->
            val body = result.andReturn().response.contentAsString
            INTERNAL_TRACES.forEach { trace ->
                assertThat(body).describedAs("$code body must not leak '$trace'").doesNotContain(trace)
            }
        }

    private companion object {
        const val AMOUNT_DETAIL = "O valor deve ser positivo e ter no máximo 2 casas decimais."

        val INTERNAL_TRACES =
            listOf(
                "Exception",
                "stackTrace",
                "\tat ",
                "at com.",
                "at org.",
                "at java.",
                "com.kotlinbitcoins",
                "org.springframework",
                "SQL",
            )
    }
}

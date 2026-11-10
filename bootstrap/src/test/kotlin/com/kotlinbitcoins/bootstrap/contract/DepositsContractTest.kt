package com.kotlinbitcoins.bootstrap.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.kotlinbitcoins.application.port.EmailContent
import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import com.kotlinbitcoins.bootstrap.support.JwtTestTokens
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

/** US2 acceptance scenarios 1–4 over the assembled application (contracts/openapi.yaml: /me/deposits). */
class DepositsContractTest : ApplicationTestBase() {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var accountId: UUID

    @BeforeEach
    fun registerAccount() {
        val body =
            mockMvc
                .post("/api/v1/accounts") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"Ana Souza","email":"ana@example.com","password":"s3nha-forte"}"""
                }.andReturn()
                .response.contentAsString
        accountId = UUID.fromString(objectMapper.readTree(body)["id"].asText())
    }

    @Test
    fun `scenario 1 - a deposit is credited at once on top of the existing balance`() {
        deposit("50.00").andExpect { status { isCreated() } }

        deposit("100.00")
            .andExpect {
                status { isCreated() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.transactionId") { isNotEmpty() }
                jsonPath("$.amount") { value("100.00") }
                jsonPath("$.balance") { value("150.00") }
                jsonPath("$.occurredAt") { value("2026-11-09T08:10:00-03:00") }
            }

        assertThat(balance()).isEqualTo("150.00")
    }

    @Test
    fun `scenario 2 - the deposit is recorded as a DEPOSIT line of the statement`() {
        val transactionId =
            objectMapper
                .readTree(deposit("100.00").andReturn().response.contentAsString)["transactionId"]
                .asText()

        val line = jdbcTemplate.queryForMap("SELECT * FROM transactions")
        assertThat(line["id"].toString()).isEqualTo(transactionId)
        assertThat(line["account_id"].toString()).isEqualTo(accountId.toString())
        assertThat(line["type"]).isEqualTo("DEPOSIT")
        assertThat((line["amount"] as BigDecimal).toPlainString()).isEqualTo("100.00")
        assertThat(line["btc_amount_sats"]).isNull()
        assertThat(line["quote"]).isNull()
    }

    @Test
    fun `scenario 3 - the client receives an e-mail with the deposited amount within 10 seconds`() {
        deposit("100.00").andExpect { status { isCreated() } }

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(emailSender.messages).hasSize(1)
        }
        val message = emailSender.messages.single()
        assertThat(message.recipientEmail.value).isEqualTo("ana@example.com")
        assertThat(message.recipientName).isEqualTo("Ana Souza")
        assertThat(message.content).isEqualTo(EmailContent.DepositConfirmed(money("100.00")))
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val sentAt = jdbcTemplate.queryForObject("SELECT sent_at FROM outbox_events", Any::class.java)
            assertThat(sentAt).describedAs("outbox sent_at").isNotNull
        }
    }

    @Test
    fun `scenario 4 - invalid amounts are refused with 400 and change nothing`() {
        deposit("100.00").andExpect { status { isCreated() } }
        val before = emailSender.messages.size

        listOf("0", "0.00", "-5", "abc", "10.123", "1e3", "").forEach { amount ->
            post("""{"amount":"$amount"}""")
                .andExpect {
                    status { isBadRequest() }
                    content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                    jsonPath("$.code") { value("INVALID_AMOUNT") }
                    jsonPath("$.detail") { value("O valor deve ser positivo e ter no máximo 2 casas decimais.") }
                }
        }
        post("{}").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_AMOUNT") }
        }

        assertThat(balance()).isEqualTo("100.00")
        assertThat(count("transactions")).isEqualTo(1)
        assertThat(count("outbox_events")).isEqualTo(1)
        assertThat(emailSender.messages).hasSize(before)
    }

    @Test
    fun `without a valid token the deposit is 401 and changes nothing`() {
        listOf(null, "Bearer ${JwtTestTokens.MALFORMED}", "Bearer ${JwtTestTokens.expired(accountId, clock.now())}")
            .forEach { authorization ->
                mockMvc
                    .post("/api/v1/me/deposits") {
                        authorization?.let { header(HttpHeaders.AUTHORIZATION, it) }
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"amount":"100.00"}"""
                    }.andExpect {
                        status { isUnauthorized() }
                        jsonPath("$.code") { value("UNAUTHORIZED") }
                    }
            }

        assertThat(balance()).isEqualTo("0.00")
    }

    @Test
    fun `a token for an account that does not exist is 401`() {
        mockMvc
            .post("/api/v1/me/deposits") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(UUID.randomUUID(), clock.now())}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount":"100.00"}"""
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("UNAUTHORIZED") }
            }
    }

    @Test
    fun `FR-031 - an e-mail provider that is down never undoes the deposit`() {
        emailSender.isFailing = true

        deposit("100.00")
            .andExpect {
                status { isCreated() }
                jsonPath("$.balance") { value("100.00") }
            }

        assertThat(balance()).isEqualTo("100.00")
        assertThat(count("transactions")).isEqualTo(1)
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(emailSender.attempts).describedAs("send attempts").isGreaterThanOrEqualTo(1)
        }
        assertThat(emailSender.messages).isEmpty()
    }

    @Test
    fun `concurrent deposits are all credited, none lost`() {
        val deposits = 10
        val results =
            (1..deposits)
                .map {
                    java.util.concurrent.CompletableFuture.supplyAsync {
                        deposit(
                            "10.00",
                        ).andReturn().response.status
                    }
                }.map { it.join() }

        assertThat(results).containsOnly(201)
        assertThat(balance()).isEqualTo("100.00")
        assertThat(count("transactions")).isEqualTo(deposits)
    }

    private fun deposit(amount: String): ResultActionsDsl = post("""{"amount":"$amount"}""")

    private fun post(body: String): ResultActionsDsl =
        mockMvc.post("/api/v1/me/deposits") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(accountId, clock.now())}")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun balance(): String =
        jdbcTemplate
            .queryForObject(
                "SELECT balance FROM accounts WHERE id = ?",
                BigDecimal::class.java,
                accountId,
            )!!
            .toPlainString()

    private fun count(table: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)!!

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value
}

package com.kotlinbitcoins.bootstrap.e2e

import com.fasterxml.jackson.databind.JsonNode
import com.kotlinbitcoins.application.port.EmailContent
import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import com.kotlinbitcoins.bootstrap.support.JwtTestTokens
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Steps 1–18 of specs/001-bitcoin-investment-api/quickstart.md, in the same order and with the same numbers, over
 * the assembled application, with WireMock as the quote source and the
 * [com.kotlinbitcoins.bootstrap.support.MutableClock] standing in for the wait the manual walkthrough does (the quote
 * cache lasts 10 s).
 */
class QuickstartFlowTest : ApplicationTestBase() {
    private lateinit var ana: UUID
    private lateinit var investmentA: String
    private lateinit var investmentB: String
    private lateinit var anaStatementIds: List<String>

    @Test
    fun `quickstart steps 1 to 18`() {
        stubQuote(buy = "400000.00", sell = "400000.00")

        step1And2Registration()
        step3WrongCredentials()
        step4And5Token()
        step6And7Deposits()
        step8CurrentQuote()
        step9And10Purchases()
        step11And12Position()
        step13And14Redemption()
        step15And16Statement()
        step17And18SecondClient()
    }

    private fun step1And2Registration() {
        mockMvc
            .post("/api/v1/accounts") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Ana","email":"ana@example.com","password":"senha-segura"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.balance") { value("0.00") }
                jsonPath("$.password") { doesNotExist() }
            }
        mockMvc
            .post("/api/v1/accounts") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Ana","email":"ANA@example.com ","password":"senha-segura"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("EMAIL_ALREADY_IN_USE") }
            }
        ana = UUID.fromString(jdbc("SELECT id FROM accounts WHERE email = 'ana@example.com'"))
    }

    /** Wrong password and unknown e-mail answer exactly the same, except for the correlation id. */
    private fun step3WrongCredentials() {
        val wrongPassword =
            api
                .login(
                    "ana@example.com",
                    "senha-errada",
                ).andExpect { status { isUnauthorized() } }
                .body()
        val unknownEmail =
            api
                .login(
                    "ninguem@example.com",
                    "senha-segura",
                ).andExpect { status { isUnauthorized() } }
                .body()

        assertThat(withoutCorrelationId(wrongPassword)).isEqualTo(withoutCorrelationId(unknownEmail))
        assertThat(wrongPassword).contains("INVALID_CREDENTIALS")
    }

    /** Login gives a one-hour token that works; no token and a tampered token are 401. */
    private fun step4And5Token() {
        val login =
            jsonMapper.readTree(
                api.login("ana@example.com", "senha-segura").andExpect { status { isOk() } }.body(),
            )
        assertThat(login["expiresIn"].asInt()).isEqualTo(3600)
        mockMvc
            .get("/api/v1/me/balance") { header(HttpHeaders.AUTHORIZATION, "Bearer ${login["accessToken"].asText()}") }
            .andExpect {
                status { isOk() }
                jsonPath("$.balance") { value("0.00") }
            }

        mockMvc.get("/api/v1/me/balance").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
        mockMvc
            .get("/api/v1/me/balance") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.tampered(ana, clock.now())}")
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("UNAUTHORIZED") }
            }
    }

    private fun step6And7Deposits() {
        api.deposit(ana, "1000.00").andExpect {
            status { isCreated() }
            jsonPath("$.balance") { value("1000.00") }
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(emailSender.messages.map { it.content }).anyMatch { it is EmailContent.DepositConfirmed }
        }

        listOf("0", "-5", "abc", "10.123").forEach { amount ->
            api.deposit(ana, amount).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_AMOUNT") }
            }
        }
        assertThat(api.balance(ana)).isEqualTo("1000.00")
    }

    private fun step8CurrentQuote() {
        api.get("/api/v1/quotes/current", ana).andExpect {
            status { isOk() }
            jsonPath("$.buyPrice") { value("400000.00") }
            jsonPath("$.sellPrice") { value("400000.00") }
            jsonPath("$.quotedAt") { value("2026-11-09T08:10:00-03:00") }
        }
    }

    /** 400.00 buys 0.001 BTC; 600.01 is more than the balance and changes nothing. */
    private fun step9And10Purchases() {
        val purchase =
            api
                .purchase(ana, "400.00")
                .andExpect {
                    status { isCreated() }
                    jsonPath("$.btcAmount") { value("0.00100000") }
                    jsonPath("$.quote") { value("400000.00") }
                    jsonPath("$.balance") { value("600.00") }
                }.body()
        investmentA = jsonMapper.readTree(purchase)["investmentId"].asText()

        api.purchase(ana, "600.01").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INSUFFICIENT_BALANCE") }
        }
        assertThat(api.balance(ana)).isEqualTo("600.00")
    }

    /** Buy price 440000.00 is +10 %; then 200.00 more (0.0005 BTC) and the buy price falls to 200000.00. */
    private fun step11And12Position() {
        stubQuote(buy = "440000.00", sell = "400000.00")
        clock.advance(Duration.ofSeconds(QUOTE_CACHE_SECONDS))
        api.get("/api/v1/me/investments", ana).andExpect {
            status { isOk() }
            jsonPath("$.investments[0].variationPercent") { value("10.00") }
            jsonPath("$.investments[0].currentValue") { value("440.00") }
        }

        val purchase =
            api
                .purchase(ana, "200.00")
                .andExpect {
                    status { isCreated() }
                    jsonPath("$.btcAmount") { value("0.00050000") }
                }.body()
        investmentB = jsonMapper.readTree(purchase)["investmentId"].asText()
        stubQuote(buy = "200000.00", sell = "400000.00")
        clock.advance(Duration.ofSeconds(QUOTE_CACHE_SECONDS))
        api.get("/api/v1/me/investments", ana).andExpect {
            status { isOk() }
            jsonPath("$.investments[0].id") { value(investmentA) }
            jsonPath("$.investments[0].currentValue") { value("200.00") }
            jsonPath("$.investments[1].id") { value(investmentB) }
            jsonPath("$.investments[1].currentValue") { value("100.00") }
            jsonPath("$.totalCurrentValue") { value("300.00") }
        }
    }

    /** 250.00 takes A whole and B in part, and the residue of B is reinvested; the rest (50.00) cannot cover 50.01. */
    private fun step13And14Redemption() {
        api.redeem(ana, "250.00").andExpect {
            status { isCreated() }
            jsonPath("$.btcSold") { value("0.00125000") }
            jsonPath("$.liquidations.length()") { value(2) }
            jsonPath("$.liquidations[0].investmentId") { value(investmentA) }
            jsonPath("$.liquidations[0].amount") { value("200.00") }
            jsonPath("$.liquidations[0].isPartial") { value(false) }
            jsonPath("$.liquidations[1].investmentId") { value(investmentB) }
            jsonPath("$.liquidations[1].amount") { value("50.00") }
            jsonPath("$.liquidations[1].btcAmount") { value("0.00025000") }
            jsonPath("$.liquidations[1].isPartial") { value(true) }
            jsonPath("$.reinvestment.btcAmount") { value("0.00025000") }
            jsonPath("$.reinvestment.purchaseQuote") { value("400000.00") }
            jsonPath("$.reinvestment.investedAmount") { value("100.00") }
            jsonPath("$.balance") { value("650.00") }
        }

        api.redeem(ana, "50.01").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("REDEMPTION_EXCEEDS_AVAILABLE") }
        }
        assertThat(api.balance(ana)).isEqualTo("650.00")
    }

    /** Every line, newest first; the lines of the redemption share the request id. An inverted range is 422. */
    private fun step15And16Statement() {
        val entries = api.json("/api/v1/me/statement", ana)["entries"].toList()

        assertThat(entries.map { it["type"].asText() })
            .containsExactlyInAnyOrder("DEPOSIT", "PURCHASE", "PURCHASE", "REDEMPTION", "REDEMPTION", "REINVESTMENT")
        val instants = entries.map { OffsetDateTime.parse(it["occurredAt"].asText()).toInstant() }
        assertThat(instants).isSortedAccordingTo(Comparator.reverseOrder())
        val redemptionLines = entries.filter { it["type"].asText() in setOf("REDEMPTION", "REINVESTMENT") }
        assertThat(redemptionLines.map { it["requestId"].asText() }.distinct()).hasSize(1)
        assertThat(redemptionLines.first()["requestId"].isNull).isFalse()
        anaStatementIds = entries.map { it["id"].asText() }

        api.get("/api/v1/me/statement?from=2026-11-10&to=2026-11-01", ana).andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INVALID_DATE_RANGE") }
        }
    }

    /** Bia buys too: the daily volume adds both clients and ignores the reinvestment; Bia only sees her own data. */
    private fun step17And18SecondClient() {
        val bia = api.register("Bia", "bia@example.com")
        api.depositOk(bia, "500.00")
        api.purchaseOk(bia, "100.00") // sell price 400000.00 → 0.00025000 BTC

        api.get("/api/v1/market/daily-volume", ana).andExpect {
            status { isOk() }
            jsonPath("$.boughtBtc") { value("0.00175000") } // 0.00100000 + 0.00050000 + 0.00025000
            jsonPath("$.soldBtc") { value("0.00125000") }
        }

        assertThat(api.json("/api/v1/me/investments", bia)["investments"].toList()).hasSize(1)
        val biaEntries: List<JsonNode> = api.json("/api/v1/me/statement", bia)["entries"].toList()
        assertThat(biaEntries.map { it["type"].asText() }).containsExactlyInAnyOrder("DEPOSIT", "PURCHASE")
        assertThat(biaEntries.map { it["id"].asText() }).doesNotContainAnyElementsOf(anaStatementIds)
    }

    private fun jdbc(sql: String): String = jdbcTemplate.queryForObject(sql, String::class.java)!!

    private fun ResultActionsDsl.body(): String = andReturn().response.contentAsString

    private fun withoutCorrelationId(body: String): String = body.replace(Regex(""","correlationId":"[^"]*""""), "")

    private companion object {
        const val QUOTE_CACHE_SECONDS = 11L
    }
}

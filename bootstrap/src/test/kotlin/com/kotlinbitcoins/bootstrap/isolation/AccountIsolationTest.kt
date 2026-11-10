package com.kotlinbitcoins.bootstrap.isolation

import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

/**
 * Isolation between clients (FR-033, SC-004): the account always comes from the token, so no route can show or touch
 * another customer's data. Ana buys 400.00 and Bia 200.00 of bitcoin at 400000.00.
 */
class AccountIsolationTest : ApplicationTestBase() {
    private lateinit var ana: UUID
    private lateinit var bia: UUID
    private lateinit var anaInvestment: String
    private lateinit var biaInvestment: String

    @BeforeEach
    fun registerAnaAndBiaWithOnePurchaseEach() {
        stubQuote(buy = "400000.00", sell = "400000.00")
        ana = api.register("Ana Souza", "ana@example.com")
        bia = api.register("Bia Lima", "bia@example.com")
        api.depositOk(ana, "1000.00")
        api.depositOk(bia, "500.00")
        anaInvestment = api.purchaseOk(ana, "400.00")["investmentId"].asText()
        clock.advance(Duration.ofMinutes(1))
        biaInvestment = api.purchaseOk(bia, "200.00")["investmentId"].asText()
    }

    @Test
    fun `balance shows only the balance of the owner of the token`() {
        assertThat(api.balance(ana)).isEqualTo("600.00")
        assertThat(api.balance(bia)).isEqualTo("300.00")
    }

    @Test
    fun `investments show only those of the owner of the token`() {
        clock.advance(Duration.ofSeconds(11))

        val anaPosition = api.json("/api/v1/me/investments", ana)
        val biaPosition = api.json("/api/v1/me/investments", bia)

        assertThat(anaPosition["investments"].map { it["id"].asText() }).containsExactly(anaInvestment)
        assertThat(biaPosition["investments"].map { it["id"].asText() }).containsExactly(biaInvestment)
        assertThat(anaPosition["totalCurrentValue"].asText()).isEqualTo("400.00")
        assertThat(biaPosition["totalCurrentValue"].asText()).isEqualTo("200.00")
    }

    @Test
    fun `the statement shows only the lines of the owner of the token`() {
        val anaLines = api.json("/api/v1/me/statement", ana)["entries"]
        val biaLines = api.json("/api/v1/me/statement", bia)["entries"]

        assertThat(anaLines.map { it["type"].asText() }).containsExactlyInAnyOrder("PURCHASE", "DEPOSIT")
        assertThat(biaLines.map { it["type"].asText() }).containsExactlyInAnyOrder("PURCHASE", "DEPOSIT")
        assertThat(anaLines.map { it["id"].asText() }).containsExactlyInAnyOrderElementsOf(transactionIds(ana))
        assertThat(biaLines.map { it["id"].asText() }).containsExactlyInAnyOrderElementsOf(transactionIds(bia))
        assertThat(anaLines.map { it["id"].asText() }).doesNotContainAnyElementsOf(biaLines.map { it["id"].asText() })
    }

    @Test
    fun `a redemption of Bia never liquidates investments of Ana`() {
        clock.advance(Duration.ofSeconds(11))

        // Bia has 200.00 in bitcoin; Ana and Bia together have 600.00, so only the token's owner limit applies.
        api.redeem(bia, "200.01").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("REDEMPTION_EXCEEDS_AVAILABLE") }
        }
        api.redeemOk(bia, "200.00")

        assertThat(investmentStatus(anaInvestment)).isEqualTo("ACTIVE")
        assertThat(investmentStatus(biaInvestment)).isEqualTo("CLOSED")
        assertThat(api.balance(ana)).isEqualTo("600.00")
        assertThat(api.balance(bia)).isEqualTo("500.00")
        assertThat(count("transactions WHERE account_id = '$ana'")).isEqualTo(2)
    }

    @Test
    fun `a token whose subject is an account that does not exist is 401 on every protected route`() {
        val ghost = UUID.randomUUID()

        listOf("/api/v1/me/balance", "/api/v1/me/investments", "/api/v1/me/statement").forEach { path ->
            api.get(path, ghost).andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("UNAUTHORIZED") }
            }
        }
        listOf("/api/v1/me/deposits", "/api/v1/me/purchases", "/api/v1/me/redemptions").forEach { path ->
            api.post(path, ghost, """{"amount":"10.00"}""").andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("UNAUTHORIZED") }
            }
        }
        assertThat(count("transactions")).isEqualTo(4)
    }

    private fun transactionIds(accountId: UUID): List<String> =
        jdbcTemplate.queryForList(
            "SELECT id FROM transactions WHERE account_id = ?",
            String::class.java,
            accountId,
        )

    private fun investmentStatus(investmentId: String): String =
        jdbcTemplate.queryForObject(
            "SELECT status FROM investments WHERE id = ?",
            String::class.java,
            UUID.fromString(investmentId),
        )!!

    private fun count(fromAndWhere: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $fromAndWhere", Int::class.java)!!
}

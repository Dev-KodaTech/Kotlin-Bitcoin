package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.EntryType
import com.kotlinbitcoins.web.core.model.Statement
import com.kotlinbitcoins.web.core.statement.StatementFilter
import com.kotlinbitcoins.web.core.time.CalendarDate
import com.kotlinbitcoins.web.data.fixtures.ContractFixtures
import com.kotlinbitcoins.web.data.support.MockApi
import com.kotlinbitcoins.web.data.support.fixtureApi
import com.kotlinbitcoins.web.data.support.respondJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpStatementGatewayTest {
    private fun date(iso: String): CalendarDate = requireNotNull(CalendarDate.parseIso(iso))

    @Test
    fun defaultFilterSendsNoQuery() =
        runTest {
            val api = fixtureApi("statement-ok@GET /me/statement")

            HttpStatementGateway(api.apiCall).statement(StatementFilter.Default)

            val request = api.requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertTrue(request.url.toString().endsWith("/api/v1/me/statement"), request.url.toString())
            assertTrue(request.url.parameters.isEmpty())
        }

    @Test
    fun rangeSendsFromAndTo() =
        runTest {
            val api = fixtureApi("statement-ok@GET /me/statement")

            HttpStatementGateway(api.apiCall)
                .statement(StatementFilter.Range(date("2026-10-01"), date("2026-11-09")))

            val url =
                api.requests
                    .single()
                    .url
                    .toString()
            assertTrue(url.endsWith("/api/v1/me/statement?from=2026-10-01&to=2026-11-09"), url)
        }

    @Test
    fun mapsTypesNullsAndRequestIdKeepingTheOrderOfTheApi() =
        runTest {
            val api = fixtureApi("statement-ok@GET /me/statement")

            val statement =
                assertIs<Outcome.Success<Statement>>(
                    HttpStatementGateway(api.apiCall).statement(StatementFilter.Default),
                ).value

            assertEquals("2026-08-11T03:00:00Z", statement.from.instant.toString())
            assertEquals(
                listOf(
                    EntryType.Redemption,
                    EntryType.Redemption,
                    EntryType.Reinvestment,
                    EntryType.Purchase,
                    EntryType.Deposit,
                ),
                statement.entries.map { it.type },
            )
            val deposit = statement.entries.last()
            assertEquals("1000.00", deposit.amount.toApiString())
            assertNull(deposit.btcAmount)
            assertNull(deposit.quote)
            assertNull(deposit.investmentId)
            assertNull(deposit.requestId)
            val purchase = statement.entries[3]
            assertEquals("0.00100000", purchase.btcAmount?.toApiString())
            assertEquals("400000.00", purchase.quote?.toPlainString())
            assertNull(purchase.requestId)
            val group = statement.entries.take(3)
            assertEquals(setOf("5c3e1a2b-4d5f-4a6b-8c7d-9e0f1a2b3c4d"), group.map { it.requestId }.toSet())
        }

    @Test
    fun emptyStatementKeepsThePeriod() =
        runTest {
            val api = fixtureApi("statement-empty@GET /me/statement")

            val statement =
                assertIs<Outcome.Success<Statement>>(
                    HttpStatementGateway(api.apiCall).statement(StatementFilter.Default),
                ).value

            assertTrue(statement.entries.isEmpty())
            assertEquals("2026-10-01T03:00:00Z", statement.from.instant.toString())
        }

    @Test
    fun invalidDateRangeIsItsOwnError() =
        runTest {
            val api = fixtureApi("invalid-date-range@GET /me/statement")
            assertEquals(
                Outcome.Failure(ApiError.InvalidDateRange),
                HttpStatementGateway(api.apiCall)
                    .statement(StatementFilter.Range(date("2026-11-01"), date("2026-11-01"))),
            )
        }

    @Test
    fun unknownTypeMakesTheStatementUnexpected() =
        runTest {
            val body =
                ContractFixtures
                    .byScenario("statement-ok@GET /me/statement")
                    .body
                    .replace("\"DEPOSIT\"", "\"TRANSFER\"")
            val api = MockApi { respondJson(body) }
            assertEquals(
                Outcome.Failure(ApiError.Unexpected),
                HttpStatementGateway(api.apiCall).statement(StatementFilter.Default),
            )
        }
}

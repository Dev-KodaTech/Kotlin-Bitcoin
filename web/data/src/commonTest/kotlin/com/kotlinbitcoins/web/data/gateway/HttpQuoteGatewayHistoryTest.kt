package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.QuoteHistory
import com.kotlinbitcoins.web.data.support.MockApi
import com.kotlinbitcoins.web.data.support.fixtureApi
import com.kotlinbitcoins.web.data.support.respondJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpQuoteGatewayHistoryTest {
    @Test
    fun pointsInTheOrderOfTheApiWithTheGapKept() =
        runTest {
            val api = fixtureApi("history-with-gap@GET /quotes/history")

            val history = assertIs<Outcome.Success<QuoteHistory>>(HttpQuoteGateway(api.apiCall).history()).value

            // 144 slots of 24 h with one missing: nothing is filled in by the client.
            assertEquals(143, history.points.size)
            assertEquals(history.points.sortedBy { it.slotAt }, history.points)
            val first = history.points.first()
            assertEquals("2026-11-08T11:20:00Z", first.slotAt.instant.toString())
            assertEquals("398000.00", first.buyPrice.toPlainString())
            assertEquals("399000.00", first.sellPrice.toPlainString())
            val slots = history.points.map { it.slotAt.instant.toString() }
            assertTrue("2026-11-09T05:00:00Z" !in slots)
            val request = api.requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertTrue(request.url.toString().endsWith("/api/v1/quotes/history"), request.url.toString())
        }

    @Test
    fun emptyHistory() =
        runTest {
            val api = fixtureApi("history-empty@GET /quotes/history")

            val history = assertIs<Outcome.Success<QuoteHistory>>(HttpQuoteGateway(api.apiCall).history()).value

            assertTrue(history.points.isEmpty())
        }

    @Test
    fun pricesKeepTheScaleOfTheSource() =
        runTest {
            val api =
                MockApi {
                    respondJson(
                        """{"points":[{"slotAt":"2026-11-09T08:10:00-03:00",""" +
                            """"buyPrice":"400000.12345678","sellPrice":"401000.5"}]}""",
                    )
                }

            val point =
                assertIs<Outcome.Success<QuoteHistory>>(
                    HttpQuoteGateway(api.apiCall).history(),
                ).value.points.single()

            assertEquals("400000.12345678", point.buyPrice.toPlainString())
            assertEquals("401000.5", point.sellPrice.toPlainString())
        }

    @Test
    fun aPointOutsideTheContractMakesTheHistoryUnexpected() =
        runTest {
            val api =
                MockApi {
                    respondJson(
                        """{"points":[{"slotAt":"2026-11-09T08:10:00","buyPrice":"1.00","sellPrice":"1.00"}]}""",
                    )
                }
            assertEquals(Outcome.Failure(ApiError.Unexpected), HttpQuoteGateway(api.apiCall).history())
        }

    @Test
    fun unauthorizedEndsTheSession() =
        runTest {
            val api = fixtureApi("unauthorized@GET /quotes/history")

            assertEquals(Outcome.Failure(ApiError.Unauthorized), HttpQuoteGateway(api.apiCall).history())
            assertEquals(1, api.tokens.unauthorizedCalls)
        }
}

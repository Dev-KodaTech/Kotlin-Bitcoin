package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Quote
import com.kotlinbitcoins.web.core.time.ApiInstant
import com.kotlinbitcoins.web.data.support.MockApi
import com.kotlinbitcoins.web.data.support.fixtureApi
import com.kotlinbitcoins.web.data.support.respondJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpQuoteGatewayCurrentTest {
    @Test
    fun quoteWithTwoDecimals() =
        runTest {
            val api = fixtureApi("quote-ok@GET /quotes/current")

            val quote = assertIs<Outcome.Success<Quote>>(HttpQuoteGateway(api.apiCall).current()).value

            assertEquals("400000.00", quote.buyPrice.toPlainString())
            assertEquals("401000.00", quote.sellPrice.toPlainString())
            assertEquals(ApiInstant.parseApi("2026-11-09T08:10:00-03:00"), Outcome.Success(quote.quotedAt))
            val request = api.requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertTrue(request.url.toString().endsWith("/api/v1/quotes/current"), request.url.toString())
        }

    @Test
    fun quoteWithEightDecimalsKeepsTheScaleOfTheSource() =
        runTest {
            val api = fixtureApi("quote-8-decimals@GET /quotes/current")

            val quote = assertIs<Outcome.Success<Quote>>(HttpQuoteGateway(api.apiCall).current()).value

            assertEquals("400000.12345678", quote.buyPrice.toPlainString())
            assertEquals("401000.5", quote.sellPrice.toPlainString())
        }

    @Test
    fun quoteUnavailableIsItsOwnError() =
        runTest {
            val api = fixtureApi("quote-unavailable@GET /quotes/current")
            assertEquals(Outcome.Failure(ApiError.QuoteUnavailable), HttpQuoteGateway(api.apiCall).current())
            assertEquals(0, api.tokens.unauthorizedCalls)
        }

    @Test
    fun anInstantWithoutOffsetIsUnexpected() =
        runTest {
            val api =
                MockApi {
                    respondJson("""{"buyPrice":"1.00","sellPrice":"1.00","quotedAt":"2026-11-09T08:10:00"}""")
                }
            assertEquals(Outcome.Failure(ApiError.Unexpected), HttpQuoteGateway(api.apiCall).current())
        }
}

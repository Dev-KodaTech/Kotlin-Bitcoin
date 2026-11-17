package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.core.money.Trend
import com.kotlinbitcoins.web.data.fixtures.ContractFixtures
import com.kotlinbitcoins.web.data.support.MockApi
import com.kotlinbitcoins.web.data.support.fixtureApi
import com.kotlinbitcoins.web.data.support.respondJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpInvestmentGatewayPositionTest {
    @Test
    fun positionKeepsTheOrderAndTheValuesOfTheApi() =
        runTest {
            val api = fixtureApi("position-ok@GET /me/investments")

            val position = assertIs<Outcome.Success<Position>>(HttpInvestmentGateway(api.apiCall).position()).value

            assertEquals("704.00", position.totalCurrentValue.toApiString())
            assertEquals("440000.00", position.quote.buyPrice.toPlainString())
            assertEquals(
                listOf(
                    "0b6f5c1e-2a3d-4e5f-8a9b-1c2d3e4f5a01",
                    "0b6f5c1e-2a3d-4e5f-8a9b-1c2d3e4f5a02",
                    "0b6f5c1e-2a3d-4e5f-8a9b-1c2d3e4f5a03",
                ),
                position.investments.map { it.id },
            )
            assertEquals(
                listOf(Trend.Up, Trend.Down, Trend.Flat),
                position.investments.map { it.variationPercent.trend },
            )
            val second = position.investments[1]
            assertEquals("231.58", second.investedAmount.toApiString())
            assertEquals("463157.89", second.purchaseQuote.toPlainString())
            assertEquals("0.00050000", second.btcAmount.toApiString())
            assertEquals("220.00", second.currentValue.toApiString())
            val request = api.requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertTrue(request.url.toString().endsWith("/api/v1/me/investments"), request.url.toString())
        }

    @Test
    fun emptyPosition() =
        runTest {
            val api = fixtureApi("position-empty@GET /me/investments")

            val position = assertIs<Outcome.Success<Position>>(HttpInvestmentGateway(api.apiCall).position()).value

            assertTrue(position.investments.isEmpty())
            assertTrue(position.totalCurrentValue.isZero())
        }

    @Test
    fun quoteUnavailableIsItsOwnError() =
        runTest {
            val api = fixtureApi("quote-unavailable@GET /me/investments")
            assertEquals(Outcome.Failure(ApiError.QuoteUnavailable), HttpInvestmentGateway(api.apiCall).position())
        }

    @Test
    fun oneValueOutsideTheContractMakesThePositionUnexpected() =
        runTest {
            val body =
                ContractFixtures
                    .byScenario("position-ok@GET /me/investments")
                    .body
                    .replace("\"-5.00\"", "\"-5\"")
            val api = MockApi { respondJson(body) }
            assertEquals(Outcome.Failure(ApiError.Unexpected), HttpInvestmentGateway(api.apiCall).position())
        }
}

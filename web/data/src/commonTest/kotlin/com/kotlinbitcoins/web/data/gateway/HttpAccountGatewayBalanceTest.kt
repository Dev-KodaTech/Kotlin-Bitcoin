package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.data.support.MockApi
import com.kotlinbitcoins.web.data.support.fixtureApi
import com.kotlinbitcoins.web.data.support.respondJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpAccountGatewayBalanceTest {
    @Test
    fun getBalanceMapsTheMoneyAsReceived() =
        runTest {
            val api = fixtureApi("balance-ok@GET /me/balance")

            val result = HttpAccountGateway(api.apiCall).balance()

            val balance = assertIs<Outcome.Success<Money>>(result).value
            assertEquals("1234.56", balance.toApiString())
            assertEquals("R$ 1.234,56", balance.format())
            val request = api.requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertTrue(request.url.toString().endsWith("/api/v1/me/balance"), request.url.toString())
        }

    @Test
    fun aBalanceOutsideTheMoneyPatternIsUnexpected() =
        runTest {
            val api = MockApi { respondJson("""{"balance":"1234.5"}""") }
            assertEquals(Outcome.Failure(ApiError.Unexpected), HttpAccountGateway(api.apiCall).balance())
        }

    @Test
    fun unauthorizedEndsTheSession() =
        runTest {
            val api = fixtureApi("unauthorized@GET /me/balance")
            assertEquals(Outcome.Failure(ApiError.Unauthorized), HttpAccountGateway(api.apiCall).balance())
            assertEquals(1, api.tokens.unauthorizedCalls)
        }
}

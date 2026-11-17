package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.DailyVolume
import com.kotlinbitcoins.web.data.support.MockApi
import com.kotlinbitcoins.web.data.support.fixtureApi
import com.kotlinbitcoins.web.data.support.respondJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpMarketGatewayTest {
    @Test
    fun volumeWithEightDecimalsAndTheDateOfTheApi() =
        runTest {
            val api = fixtureApi("daily-volume-ok@GET /market/daily-volume")

            val volume = assertIs<Outcome.Success<DailyVolume>>(HttpMarketGateway(api.apiCall).dailyVolume()).value

            assertEquals("2026-11-09", volume.date.toIso())
            assertEquals("09/11/2026", volume.date.toUserString())
            assertEquals("0.00300000", volume.boughtBtc.toApiString())
            assertEquals("0,00300000 BTC", volume.boughtBtc.format())
            assertEquals("0.00050000", volume.soldBtc.toApiString())
            val request = api.requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertTrue(request.url.toString().endsWith("/api/v1/market/daily-volume"), request.url.toString())
        }

    @Test
    fun unauthorizedEndsTheSession() =
        runTest {
            val api = fixtureApi("unauthorized@GET /market/daily-volume")

            assertEquals(Outcome.Failure(ApiError.Unauthorized), HttpMarketGateway(api.apiCall).dailyVolume())
            assertEquals(1, api.tokens.unauthorizedCalls)
        }

    @Test
    fun aVolumeWithoutEightDecimalsIsUnexpected() =
        runTest {
            val api = MockApi { respondJson("""{"date":"2026-11-09","boughtBtc":"0.003","soldBtc":"0.00050000"}""") }
            assertEquals(Outcome.Failure(ApiError.Unexpected), HttpMarketGateway(api.apiCall).dailyVolume())
        }

    @Test
    fun anInvalidDateIsUnexpected() =
        runTest {
            val api =
                MockApi { respondJson("""{"date":"09/11/2026","boughtBtc":"0.00300000","soldBtc":"0.00050000"}""") }
            assertEquals(Outcome.Failure(ApiError.Unexpected), HttpMarketGateway(api.apiCall).dailyVolume())
        }
}

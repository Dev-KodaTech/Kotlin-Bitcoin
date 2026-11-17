package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Profile
import com.kotlinbitcoins.web.data.support.MockApi
import com.kotlinbitcoins.web.data.support.fixtureApi
import com.kotlinbitcoins.web.data.support.respondJson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpProfileGatewayTest {
    @Test
    fun getMeMapsNameAndEmail() =
        runTest {
            val api = fixtureApi("profile-ok@GET /me")

            val result = HttpProfileGateway(api.apiCall).profile()

            assertEquals(Outcome.Success(Profile("Maria Souza", "maria@example.com")), result)
            val request = api.requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertTrue(request.url.toString().endsWith("/api/v1/me"), request.url.toString())
            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
        }

    @Test
    fun unauthorizedEndsTheSession() =
        runTest {
            val api = fixtureApi("unauthorized@GET /me")

            assertEquals(Outcome.Failure(ApiError.Unauthorized), HttpProfileGateway(api.apiCall).profile())
            assertEquals(1, api.tokens.unauthorizedCalls)
        }

    @Test
    fun aBodyOutsideTheContractIsUnexpected() =
        runTest {
            val api = MockApi { respondJson("""{"nome":"Maria"}""") }
            assertEquals(Outcome.Failure(ApiError.Unexpected), HttpProfileGateway(api.apiCall).profile())
        }
}

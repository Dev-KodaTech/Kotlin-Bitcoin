package com.kotlinbitcoins.web.data.http

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.data.support.MockApi
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpClientSecurityTest {
    private val authenticated =
        listOf(
            "me",
            "me/balance",
            "me/statement",
            "me/investments",
            "quotes/current",
            "quotes/history",
            "market/daily-volume",
        )

    @Test
    fun bearerTokenOnAuthenticatedRoutes() =
        runTest {
            val api = MockApi()
            authenticated.forEach { api.client.get(it) }
            listOf("me/deposits", "me/purchases", "me/redemptions").forEach { api.client.post(it) }
            assertEquals(10, api.requests.size)
            api.requests.forEach { request ->
                assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization], request.url.encodedPath)
            }
        }

    @Test
    fun noTokenOnRegistrationAndLogin() =
        runTest {
            val api = MockApi()
            api.client.post("accounts")
            api.client.post("auth/login")
            assertEquals(listOf("/api/v1/accounts", "/api/v1/auth/login"), api.requests.map { it.url.encodedPath })
            api.requests.forEach { assertNull(it.headers[HttpHeaders.Authorization], it.url.encodedPath) }
        }

    @Test
    fun theTokenNeverGoesToTheUrl() =
        runTest {
            val api = MockApi()
            authenticated.forEach { api.client.get(it) }
            api.requests.forEach { assertFalse(it.url.toString().contains("test-token"), it.url.toString()) }
        }

    @Test
    fun withoutAValidSessionTheRequestIsNotSent() =
        runTest {
            val api = MockApi()
            api.tokens.token = null
            val outcome =
                api.apiCall.read(String.serializer(), { Outcome.Success(it) }) {
                    method = HttpMethod.Get
                    url("me/balance")
                }
            assertEquals(Outcome.Failure(ApiError.Unauthorized), outcome)
            assertTrue(api.requests.isEmpty())
            assertEquals(0, api.tokens.unauthorizedCalls)
        }

    @Test
    fun theClientHasNoLoggingPlugin() {
        val client = MockApi().client
        assertTrue(client.pluginOrNull(AuthHeaderPlugin) != null)
        // The registry of installed plugins (key "ApplicationPluginRegistry") is internal in Ktor; read it by name.
        val registryKey = client.attributes.allKeys.single { it.name == "ApplicationPluginRegistry" }

        @Suppress("UNCHECKED_CAST")
        val installed = client.attributes[registryKey as AttributeKey<Attributes>].allKeys.map { it.name }
        assertTrue(installed.contains(AuthHeaderPlugin.key.name), "installed plugins: $installed")
        assertFalse(installed.any { it.contains("Logging", ignoreCase = true) }, "installed plugins: $installed")
    }
}

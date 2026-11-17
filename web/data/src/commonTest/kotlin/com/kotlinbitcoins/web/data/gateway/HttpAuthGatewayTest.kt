package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.FieldError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.AccountCreated
import com.kotlinbitcoins.web.core.model.LoginResult
import com.kotlinbitcoins.web.data.fixtures.ContractFixtures
import com.kotlinbitcoins.web.data.support.MockApi
import com.kotlinbitcoins.web.data.support.respondJson
import com.kotlinbitcoins.web.data.support.respondProblem
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class HttpAuthGatewayTest {
    private fun fixture(scenario: String): Pair<String, HttpStatusCode> {
        val fixture = ContractFixtures.byScenario(scenario)
        return fixture.body to HttpStatusCode.fromValue(fixture.entry.status)
    }

    private fun MockApi.gateway() = HttpAuthGateway(apiCall)

    private fun HttpRequestData.jsonBody(): JsonObject =
        Json.parseToJsonElement((body as OutgoingContent.ByteArrayContent).bytes().decodeToString()) as JsonObject

    @Test
    fun registerSendsNameEmailAndPasswordAndMaps201() =
        runTest {
            val (body, status) = fixture("register-ok@POST /accounts")
            val api = MockApi { respondJson(body, status) }

            val result = api.gateway().register("Ana Souza", "ana@example.com", "s3nha-forte")

            assertEquals(
                Outcome.Success(AccountCreated("3f1c2a9e-7b4d-4e8a-9c21-5d6e7f8a9b0c", "Ana Souza", "ana@example.com")),
                result,
            )
            val request = api.requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertTrue(request.url.toString().endsWith("/api/v1/accounts"), request.url.toString())
            assertEquals(
                JsonObject(
                    mapOf(
                        "name" to JsonPrimitive("Ana Souza"),
                        "email" to JsonPrimitive("ana@example.com"),
                        "password" to JsonPrimitive("s3nha-forte"),
                    ),
                ),
                request.jsonBody(),
            )
            assertNull(request.headers[HttpHeaders.Authorization])
        }

    @Test
    fun loginSendsEmailAndPasswordAndMaps200() =
        runTest {
            val (body, status) = fixture("login-ok@POST /auth/login")
            val api = MockApi { respondJson(body, status) }

            val result = api.gateway().login("ana@example.com", "s3nha-forte")

            val login = assertIs<Outcome.Success<LoginResult>>(result).value
            assertTrue(login.accessToken.startsWith("eyJ"))
            assertEquals(3600.seconds, login.expiresIn)
            assertEquals("LoginResult(****, expiresIn=1h)", login.toString())
            val request = api.requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertTrue(request.url.toString().endsWith("/api/v1/auth/login"), request.url.toString())
            assertEquals(
                JsonObject(
                    mapOf("email" to JsonPrimitive("ana@example.com"), "password" to JsonPrimitive("s3nha-forte")),
                ),
                request.jsonBody(),
            )
            assertNull(request.headers[HttpHeaders.Authorization])
        }

    @Test
    fun loginWithoutAPositiveExpiryIsUnexpected() =
        runTest {
            val api = MockApi { respondJson("""{"accessToken":"t","tokenType":"Bearer","expiresIn":0}""") }
            assertEquals(Outcome.Failure(ApiError.Unexpected), api.gateway().login("ana@example.com", "s3nha-forte"))
        }

    @Test
    fun emailAlreadyInUse() =
        runTest {
            val (body, status) = fixture("email-already-in-use@POST /accounts")
            val api = MockApi { respondProblem(body, status) }
            assertEquals(
                Outcome.Failure(ApiError.EmailAlreadyInUse),
                api.gateway().register("Ana", "ana@example.com", "s3nha-forte"),
            )
        }

    @Test
    fun validationErrorKeepsTheFieldErrors() =
        runTest {
            val expected = ApiError.Validation(listOf(FieldError("email", "E-mail em formato inválido.")))
            val (registerBody, registerStatus) = fixture("validation-error@POST /accounts")
            val register = MockApi { respondProblem(registerBody, registerStatus) }
            assertEquals(Outcome.Failure(expected), register.gateway().register("Ana", "ana", "s3nha-forte"))

            val (loginBody, loginStatus) = fixture("validation-error@POST /auth/login")
            val login = MockApi { respondProblem(loginBody, loginStatus) }
            assertEquals(Outcome.Failure(expected), login.gateway().login("ana", "s3nha-forte"))
        }

    @Test
    fun invalidCredentialsDoesNotEndASession() =
        runTest {
            val (body, status) = fixture("invalid-credentials@POST /auth/login")
            val api = MockApi { respondProblem(body, status) }
            assertEquals(
                Outcome.Failure(ApiError.InvalidCredentials),
                api.gateway().login("ana@example.com", "senha-errada"),
            )
            assertEquals(0, api.tokens.unauthorizedCalls)
        }

    @Test
    fun transportFailureIsNoConnection() =
        runTest {
            val api = MockApi { error("network down") }
            assertEquals(Outcome.Failure(ApiError.NoConnection), api.gateway().login("ana@example.com", "s3nha-forte"))
            assertEquals(
                Outcome.Failure(ApiError.NoConnection),
                api.gateway().register("Ana", "ana@example.com", "s3nha-forte"),
            )
        }
}

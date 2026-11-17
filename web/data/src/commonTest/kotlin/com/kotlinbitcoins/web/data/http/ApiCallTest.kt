package com.kotlinbitcoins.web.data.http

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.data.fixtures.ContractFixtures
import com.kotlinbitcoins.web.data.support.MockApi
import com.kotlinbitcoins.web.data.support.respondJson
import com.kotlinbitcoins.web.data.support.respondProblem
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiCallTest {
    @Serializable
    private data class AmountDto(
        val amount: String,
    )

    private fun toMoney(dto: AmountDto): Outcome<ApiError, Money> = Money.parseApi(dto.amount)

    private suspend fun MockApi.readBalance(): Outcome<ApiError, Money> =
        apiCall.read(AmountDto.serializer(), ::toMoney) {
            method = HttpMethod.Get
            url("me/balance")
        }

    private suspend fun MockApi.login(): Outcome<ApiError, Money> =
        apiCall.read(AmountDto.serializer(), ::toMoney) {
            method = HttpMethod.Post
            url("auth/login")
        }

    private suspend fun MockApi.financial(path: String): Outcome<OperationError, Money> =
        apiCall.financial(AmountDto.serializer(), ::toMoney) {
            method = HttpMethod.Post
            url(path)
        }

    private val financialPaths = listOf("me/deposits", "me/purchases", "me/redemptions")

    @Test
    fun successWithAValidBody() =
        runTest {
            val api = MockApi { respondJson("""{"amount":"100.50","extra":"ignored"}""") }
            assertEquals(
                Outcome.Success(Money.parseApi("100.50").let { (it as Outcome.Success).value }),
                api.readBalance(),
            )
        }

    @Test
    fun successWithAValueOutsideTheContractIsUnexpected() =
        runTest {
            val api = MockApi { respondJson("""{"amount":"1.5"}""") }
            assertEquals(Outcome.Failure(ApiError.Unexpected), api.readBalance())
        }

    @Test
    fun successWithAMalformedBodyIsUnexpected() =
        runTest {
            val api = MockApi { respondJson("""{"value":"1.50"}""") }
            assertEquals(Outcome.Failure(ApiError.Unexpected), api.readBalance())
            val html = MockApi { respond("<html></html>", HttpStatusCode.OK) }
            assertEquals(Outcome.Failure(ApiError.Unexpected), html.readBalance())
        }

    @Test
    fun undocumentedStatusWithoutProblemIsUnexpected() =
        runTest {
            listOf(
                HttpStatusCode.fromValue(418),
                HttpStatusCode.BadGateway,
                HttpStatusCode.InternalServerError,
            ).forEach {
                val api = MockApi { _ -> respond("oops", it) }
                assertEquals(Outcome.Failure(ApiError.Unexpected), api.readBalance(), "$it")
            }
        }

    @Test
    fun problemOfAReadIsTheMappedError() =
        runTest {
            val fixture = ContractFixtures.byScenario("quote-unavailable@GET /quotes/current")
            val api = MockApi { respondProblem(fixture.body, HttpStatusCode.ServiceUnavailable) }
            assertEquals(Outcome.Failure(ApiError.QuoteUnavailable), api.readBalance())
        }

    @Test
    fun transportFailureOnAReadOrLoginIsNoConnection() =
        runTest {
            val api = MockApi { error("connection refused") }
            assertEquals(Outcome.Failure(ApiError.NoConnection), api.readBalance())
            assertEquals(Outcome.Failure(ApiError.NoConnection), api.login())
            val timeout = MockApi { request -> throw HttpRequestTimeoutException(request.url.toString(), 30_000) }
            assertEquals(Outcome.Failure(ApiError.NoConnection), timeout.readBalance())
        }

    @Test
    fun transportFailureTimeoutAndGatewayErrorsAfterAFinancialPostAreUncertain() =
        runTest {
            val failures =
                listOf<MockApi>(
                    MockApi { error("connection reset") },
                    MockApi { request -> throw HttpRequestTimeoutException(request.url.toString(), 30_000) },
                    MockApi { respond("Bad Gateway", HttpStatusCode.BadGateway) },
                    MockApi { respond("Gateway Timeout", HttpStatusCode.GatewayTimeout) },
                )
            failures.forEach { api ->
                financialPaths.forEach { path ->
                    assertEquals(Outcome.Failure(OperationError.Uncertain), api.financial(path), path)
                }
            }
        }

    @Test
    fun aFinancialSuccessWithABodyOutsideTheContractIsUncertain() =
        runTest {
            val api = MockApi { respondJson("""{"amount":"1.5"}""", HttpStatusCode.Created) }
            assertEquals(Outcome.Failure(OperationError.Uncertain), api.financial("me/purchases"))
        }

    @Test
    fun aProblemAfterAFinancialPostIsADefinitiveRejection() =
        runTest {
            val cases =
                mapOf(
                    "SERVICE_UNAVAILABLE" to (HttpStatusCode.ServiceUnavailable to ApiError.ServiceUnavailable),
                    "INTERNAL_ERROR" to (HttpStatusCode.InternalServerError to ApiError.Unexpected),
                    "INSUFFICIENT_BALANCE" to (HttpStatusCode.UnprocessableEntity to ApiError.InsufficientBalance),
                    "QUOTE_UNAVAILABLE" to (HttpStatusCode.ServiceUnavailable to ApiError.QuoteUnavailable),
                )
            cases.forEach { (code, statusAndError) ->
                val (status, error) = statusAndError
                val api = MockApi { respondProblem(ContractFixtures.byFile("problem/$code.json"), status) }
                financialPaths.forEach { path ->
                    assertEquals(Outcome.Failure(OperationError.Rejected(error)), api.financial(path), "$code $path")
                }
            }
        }

    @Test
    fun aSuccessfulFinancialPost() =
        runTest {
            val api = MockApi { respondJson("""{"amount":"400.00"}""", HttpStatusCode.Created) }
            assertEquals(
                Outcome.Success(
                    Money.parseApi("400.00").let {
                        (it as Outcome.Success).value
                    },
                ),
                api.financial("me/deposits"),
            )
        }

    @Test
    fun unauthorizedEndsTheSession() =
        runTest {
            val body = ContractFixtures.byScenario("unauthorized@GET /me/balance").body
            val api = MockApi { respondProblem(body, HttpStatusCode.Unauthorized) }
            assertEquals(Outcome.Failure(ApiError.Unauthorized), api.readBalance())
            assertEquals(1, api.tokens.unauthorizedCalls)
            assertEquals(Outcome.Failure(OperationError.Rejected(ApiError.Unauthorized)), api.financial("me/deposits"))
            assertEquals(2, api.tokens.unauthorizedCalls)
        }

    @Test
    fun invalidCredentialsDoNotEndTheSession() =
        runTest {
            val body = ContractFixtures.byScenario("invalid-credentials@POST /auth/login").body
            val api = MockApi { respondProblem(body, HttpStatusCode.Unauthorized) }
            assertEquals(Outcome.Failure(ApiError.InvalidCredentials), api.login())
            assertEquals(0, api.tokens.unauthorizedCalls)
        }

    @Test
    fun anExpiredSessionDoesNotSendTheRequest() =
        runTest {
            val api = MockApi()
            api.tokens.token = null
            assertEquals(Outcome.Failure(ApiError.Unauthorized), api.readBalance())
            assertEquals(Outcome.Failure(OperationError.Rejected(ApiError.Unauthorized)), api.financial("me/purchases"))
            assertEquals(emptyList(), api.requests)
        }
}

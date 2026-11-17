package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.DepositResult
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.time.ApiInstant
import com.kotlinbitcoins.web.data.support.MockApi
import com.kotlinbitcoins.web.data.support.fixtureApi
import com.kotlinbitcoins.web.data.support.respondJson
import io.ktor.client.engine.mock.respond
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
import kotlin.test.assertTrue

class HttpAccountGatewayDepositTest {
    private fun amount(raw: String): Money = (Money.parseApi(raw) as Outcome.Success).value

    @Test
    fun postsTheAmountAsTheContractStringAndMapsTheResult() =
        runTest {
            val api = fixtureApi("deposit-ok@POST /me/deposits")

            val result = HttpAccountGateway(api.apiCall).deposit(amount("1234.56"))

            val deposit = assertIs<Outcome.Success<DepositResult>>(result).value
            assertEquals("7c9e6679-7425-40de-944b-e07fc1f90ae7", deposit.transactionId)
            assertEquals("R$ 100,00", deposit.amount.format())
            assertEquals("R$ 150,00", deposit.balance.format())
            assertEquals(ApiInstant.parseApi("2026-11-09T08:10:00-03:00"), Outcome.Success(deposit.occurredAt))

            val request = api.requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertTrue(request.url.toString().endsWith("/api/v1/me/deposits"), request.url.toString())
            assertTrue(request.headers[HttpHeaders.Authorization].orEmpty().startsWith("Bearer "))
            val body =
                Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            assertEquals(JsonObject(mapOf("amount" to JsonPrimitive("1234.56"))), body)
        }

    @Test
    fun invalidAmountIsADefinitiveRejection() =
        runTest {
            val api = fixtureApi("invalid-amount@POST /me/deposits")
            assertEquals(
                Outcome.Failure(OperationError.Rejected(ApiError.InvalidAmount)),
                HttpAccountGateway(api.apiCall).deposit(amount("0.01")),
            )
        }

    @Test
    fun transportFailureAndBadGatewayAreUncertain() =
        runTest {
            val failures =
                listOf(
                    MockApi { error("connection reset") },
                    MockApi { respond("Bad Gateway", HttpStatusCode.BadGateway) },
                )
            failures.forEach { api ->
                assertEquals(
                    Outcome.Failure(OperationError.Uncertain),
                    HttpAccountGateway(api.apiCall).deposit(amount("10.00")),
                )
                assertEquals(1, api.requests.size)
            }
        }

    @Test
    fun aCreatedBodyOutsideTheContractIsUncertain() =
        runTest {
            val api =
                MockApi {
                    respondJson(
                        """{"transactionId":"t","amount":"10.0","balance":"10.00",""" +
                            """"occurredAt":"2026-11-09T08:10:00Z"}""",
                        HttpStatusCode.Created,
                    )
                }
            assertEquals(
                Outcome.Failure(OperationError.Uncertain),
                HttpAccountGateway(api.apiCall).deposit(amount("10.00")),
            )
        }
}

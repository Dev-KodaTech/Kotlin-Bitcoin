package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.PurchaseResult
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

class HttpInvestmentGatewayPurchaseTest {
    private fun amount(raw: String): Money = (Money.parseApi(raw) as Outcome.Success).value

    @Test
    fun postsTheAmountAsTheContractStringAndMapsTheResult() =
        runTest {
            val api = fixtureApi("purchase-ok@POST /me/purchases")

            val result = HttpInvestmentGateway(api.apiCall).purchase(amount("400.00"))

            val purchase = assertIs<Outcome.Success<PurchaseResult>>(result).value
            assertEquals("0b6f5c1e-2a3d-4e5f-8a9b-1c2d3e4f5a01", purchase.investmentId)
            assertEquals("9a1f0c2e-3b4d-4c5e-8f6a-7b8c9d0e1f2a", purchase.transactionId)
            assertEquals("R$ 400,00", purchase.amount.format())
            assertEquals("0,00100000 BTC", purchase.btcAmount.format())
            assertEquals("R$ 400.000,00", purchase.quote.format())
            assertEquals("R$ 600,00", purchase.balance.format())
            assertEquals(ApiInstant.parseApi("2026-11-09T08:10:00-03:00"), Outcome.Success(purchase.purchasedAt))

            val request = api.requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertTrue(request.url.toString().endsWith("/api/v1/me/purchases"), request.url.toString())
            assertTrue(request.headers[HttpHeaders.Authorization].orEmpty().startsWith("Bearer "))
            val body =
                Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            assertEquals(JsonObject(mapOf("amount" to JsonPrimitive("400.00"))), body)
        }

    @Test
    fun theQuoteKeepsEveryDecimalAndTheBtcKeepsEight() =
        runTest {
            val api =
                MockApi {
                    respondJson(
                        """{"investmentId":"i","transactionId":"t","amount":"100.00","btcAmount":"0.00024999",""" +
                            """"quote":"400000.12345678","purchasedAt":"2026-11-09T08:10:00-03:00","balance":"0.00"}""",
                        HttpStatusCode.Created,
                    )
                }

            val purchase =
                assertIs<Outcome.Success<PurchaseResult>>(HttpInvestmentGateway(api.apiCall).purchase(amount("100.00")))
                    .value

            assertEquals("0.00024999", purchase.btcAmount.toApiString())
            assertEquals("400000.12345678", purchase.quote.toPlainString())
            assertEquals("R$ 400.000,12345678", purchase.quote.format())
        }

    @Test
    fun definitiveRefusalsAreRejected() =
        runTest {
            val cases =
                mapOf(
                    "insufficient-balance@POST /me/purchases" to ApiError.InsufficientBalance,
                    "invalid-amount@POST /me/purchases" to ApiError.InvalidAmount,
                    "quote-unavailable@POST /me/purchases" to ApiError.QuoteUnavailable,
                    "service-unavailable@POST /me/purchases" to ApiError.ServiceUnavailable,
                )
            cases.forEach { (scenario, error) ->
                val api = fixtureApi(scenario)
                assertEquals(
                    Outcome.Failure(OperationError.Rejected(error)),
                    HttpInvestmentGateway(api.apiCall).purchase(amount("100.01")),
                    scenario,
                )
            }
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
                    HttpInvestmentGateway(api.apiCall).purchase(amount("10.00")),
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
                        """{"investmentId":"i","transactionId":"t","amount":"10.00","btcAmount":"0.001",""" +
                            """"quote":"400000.00","purchasedAt":"2026-11-09T08:10:00Z","balance":"0.00"}""",
                        HttpStatusCode.Created,
                    )
                }
            assertEquals(
                Outcome.Failure(OperationError.Uncertain),
                HttpInvestmentGateway(api.apiCall).purchase(amount("10.00")),
            )
        }
}

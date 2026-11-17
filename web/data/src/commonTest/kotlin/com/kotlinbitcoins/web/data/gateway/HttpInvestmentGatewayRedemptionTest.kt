package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.RedemptionResult
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpInvestmentGatewayRedemptionTest {
    private fun amount(raw: String): Money = (Money.parseApi(raw) as Outcome.Success).value

    @Test
    fun aPartialRedemptionMapsTheLiquidationsAndTheReinvestment() =
        runTest {
            val api = fixtureApi("redemption-partial@POST /me/redemptions")

            val result = HttpInvestmentGateway(api.apiCall).redeem(amount("500.00"))

            val redemption = assertIs<Outcome.Success<RedemptionResult>>(result).value
            assertEquals("5c3e1a2b-4d5f-4a6b-8c7d-9e0f1a2b3c4d", redemption.requestId)
            assertEquals("R$ 500,00", redemption.amount.format())
            assertEquals("0,00125000 BTC", redemption.btcSold.format())
            assertEquals("R$ 400.000,00", redemption.quote.format())
            assertEquals("R$ 900,00", redemption.balance.format())
            assertEquals(ApiInstant.parseApi("2026-11-09T09:00:00-03:00"), Outcome.Success(redemption.occurredAt))

            assertEquals(2, redemption.liquidations.size)
            val (first, second) = redemption.liquidations
            assertEquals("0b6f5c1e-2a3d-4e5f-8a9b-1c2d3e4f5a01", first.investmentId)
            assertEquals("R$ 400,00", first.amount.format())
            assertEquals("0,00100000 BTC", first.btcAmount.format())
            assertEquals(false, first.isPartial)
            assertEquals("R$ 100,00", second.amount.format())
            assertEquals("0,00025000 BTC", second.btcAmount.format())
            assertEquals(true, second.isPartial)

            val reinvestment = assertNotNull(redemption.reinvestment)
            assertEquals("2d8b7e3a-4c5f-4a7b-8c1d-3e4f5a6b7c03", reinvestment.investmentId)
            assertEquals("0,00025000 BTC", reinvestment.btcAmount.format())
            assertEquals("R$ 100,00", reinvestment.investedAmount.format())
            assertEquals("R$ 400.000,00", reinvestment.purchaseQuote.format())
            assertEquals(ApiInstant.parseApi("2026-11-09T08:20:00-03:00"), Outcome.Success(reinvestment.purchasedAt))

            val request = api.requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertTrue(request.url.toString().endsWith("/api/v1/me/redemptions"), request.url.toString())
            assertTrue(request.headers[HttpHeaders.Authorization].orEmpty().startsWith("Bearer "))
            val body =
                Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            assertEquals(JsonObject(mapOf("amount" to JsonPrimitive("500.00"))), body)
        }

    @Test
    fun aFullRedemptionHasNoReinvestment() =
        runTest {
            val api = fixtureApi("redemption-full@POST /me/redemptions")

            val redemption =
                assertIs<Outcome.Success<RedemptionResult>>(HttpInvestmentGateway(api.apiCall).redeem(amount("400.00")))
                    .value

            assertNull(redemption.reinvestment)
            assertEquals(listOf(false), redemption.liquidations.map { it.isPartial })
        }

    @Test
    fun theQuoteKeepsEveryDecimal() =
        runTest {
            val api =
                MockApi {
                    respondJson(
                        """{"requestId":"r","amount":"100.00","btcSold":"0.00024999","quote":"400000.12345678",""" +
                            """"balance":"100.00","occurredAt":"2026-11-09T09:00:00-03:00","liquidations":[],""" +
                            """"reinvestment":null}""",
                        HttpStatusCode.Created,
                    )
                }

            val redemption =
                assertIs<Outcome.Success<RedemptionResult>>(HttpInvestmentGateway(api.apiCall).redeem(amount("100.00")))
                    .value

            assertEquals("0.00024999", redemption.btcSold.toApiString())
            assertEquals("400000.12345678", redemption.quote.toPlainString())
        }

    @Test
    fun definitiveRefusalsAreRejected() =
        runTest {
            val cases =
                mapOf(
                    "redemption-exceeds-available@POST /me/redemptions" to ApiError.RedemptionExceedsAvailable,
                    "invalid-amount@POST /me/redemptions" to ApiError.InvalidAmount,
                    "quote-unavailable@POST /me/redemptions" to ApiError.QuoteUnavailable,
                    "service-unavailable@POST /me/redemptions" to ApiError.ServiceUnavailable,
                )
            cases.forEach { (scenario, error) ->
                val api = fixtureApi(scenario)
                assertEquals(
                    Outcome.Failure(OperationError.Rejected(error)),
                    HttpInvestmentGateway(api.apiCall).redeem(amount("100.01")),
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
                    HttpInvestmentGateway(api.apiCall).redeem(amount("10.00")),
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
                        """{"requestId":"r","amount":"10.00","btcSold":"0.001","quote":"400000.00",""" +
                            """"balance":"0.00","occurredAt":"2026-11-09T09:00:00-03:00","liquidations":[],""" +
                            """"reinvestment":null}""",
                        HttpStatusCode.Created,
                    )
                }
            assertEquals(
                Outcome.Failure(OperationError.Uncertain),
                HttpInvestmentGateway(api.apiCall).redeem(amount("10.00")),
            )
        }
}

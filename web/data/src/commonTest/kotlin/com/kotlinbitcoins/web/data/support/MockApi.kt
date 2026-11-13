package com.kotlinbitcoins.web.data.support

import com.kotlinbitcoins.web.data.http.ApiCall
import com.kotlinbitcoins.web.data.http.HttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

const val BASE_URL = "http://localhost:8081/api/v1/"

/** A client of HttpClientFactory over a MockEngine, recording every request that reached the network. */
class MockApi(
    val tokens: FakeSessionTokenProvider = FakeSessionTokenProvider(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondJson("{}") },
) {
    val requests = mutableListOf<HttpRequestData>()

    val client =
        HttpClientFactory.create(
            baseUrl = BASE_URL,
            tokenProvider = tokens,
            engine =
                MockEngine { request ->
                    requests += request
                    handler(request)
                },
        )

    val apiCall = ApiCall(client, tokens)
}

fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

fun MockRequestHandleScope.respondProblem(
    body: String,
    status: HttpStatusCode,
): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/problem+json"))

package com.kotlinbitcoins.web.e2e.support

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/** Direct calls to the API (same origin as the client, `e2e.apiUrl`) to prepare data and check effects (W-17). */
class ApiClient(
    private val apiUrl: String = System.getProperty("e2e.apiUrl", "http://localhost:8081/api/v1").trimEnd('/'),
) {
    data class Response(
        val status: Int,
        val body: String,
        val headers: Map<String, List<String>>,
    )

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)).build()

    fun uniqueEmail(): String = "e2e-${UUID.randomUUID()}@example.com"

    fun register(
        name: String,
        email: String,
        password: String,
    ): Response = post("/accounts", """{"name":${json(name)},"email":${json(email)},"password":${json(password)}}""")

    /** Returns the access token. */
    fun login(
        email: String,
        password: String,
    ): String {
        val response = post("/auth/login", """{"email":${json(email)},"password":${json(password)}}""")
        check(response.status == HTTP_OK) { "login failed: ${response.status}" }
        return checkNotNull(Regex("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").find(response.body)).groupValues[1]
    }

    fun deposit(
        token: String,
        amount: String,
    ): Response = post("/me/deposits", """{"amount":${json(amount)}}""", token)

    fun purchase(
        token: String,
        amount: String,
    ): Response = post("/me/purchases", """{"amount":${json(amount)}}""", token)

    fun redeem(
        token: String,
        amount: String,
    ): Response = post("/me/redemptions", """{"amount":${json(amount)}}""", token)

    fun statement(
        token: String,
        query: String = "",
    ): Response = get("/me/statement" + if (query.isEmpty()) "" else "?$query", token)

    fun get(
        path: String,
        token: String? = null,
    ): Response = send(request(path, token).GET().build())

    private fun post(
        path: String,
        body: String,
        token: String? = null,
    ): Response =
        send(
            request(path, token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
        )

    private fun request(
        path: String,
        token: String?,
    ): HttpRequest.Builder =
        HttpRequest
            .newBuilder(URI.create(apiUrl + path))
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .apply { if (token != null) header("Authorization", "Bearer $token") }

    private fun send(request: HttpRequest): Response {
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return Response(response.statusCode(), response.body(), response.headers().map())
    }

    private fun json(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private companion object {
        const val TIMEOUT_SECONDS = 30L
        const val HTTP_OK = 200
    }
}

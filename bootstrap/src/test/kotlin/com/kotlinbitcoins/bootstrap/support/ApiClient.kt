package com.kotlinbitcoins.bootstrap.support

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * Thin client over the assembled application's HTTP API for the end-to-end tests. Every call goes through MockMvc
 * and the real filters, so tokens are validated as in production. Calls made `as` an account carry a valid JWT for it
 * at the current [MutableClock] time; `*Ok` helpers also assert the expected success status.
 */
class ApiClient(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val clock: MutableClock,
) {
    fun bearer(accountId: UUID): String = "Bearer ${JwtTestTokens.valid(accountId, clock.now())}"

    fun register(
        name: String,
        email: String,
        password: String = DEFAULT_PASSWORD,
    ): UUID {
        val body =
            mockMvc
                .post("/api/v1/accounts") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"$name","email":"$email","password":"$password"}"""
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        return UUID.fromString(objectMapper.readTree(body)["id"].asText())
    }

    fun login(
        email: String,
        password: String = DEFAULT_PASSWORD,
    ): ResultActionsDsl =
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }

    fun get(
        path: String,
        accountId: UUID,
    ): ResultActionsDsl = mockMvc.get(path) { header(HttpHeaders.AUTHORIZATION, bearer(accountId)) }

    fun post(
        path: String,
        accountId: UUID,
        body: String,
    ): ResultActionsDsl =
        mockMvc.post(path) {
            header(HttpHeaders.AUTHORIZATION, bearer(accountId))
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    fun deposit(
        accountId: UUID,
        amount: String,
    ): ResultActionsDsl = post("/api/v1/me/deposits", accountId, """{"amount":"$amount"}""")

    fun purchase(
        accountId: UUID,
        amount: String,
    ): ResultActionsDsl = post("/api/v1/me/purchases", accountId, """{"amount":"$amount"}""")

    fun redeem(
        accountId: UUID,
        amount: String,
    ): ResultActionsDsl = post("/api/v1/me/redemptions", accountId, """{"amount":"$amount"}""")

    fun depositOk(
        accountId: UUID,
        amount: String,
    ): JsonNode = created(deposit(accountId, amount))

    fun purchaseOk(
        accountId: UUID,
        amount: String,
    ): JsonNode = created(purchase(accountId, amount))

    fun redeemOk(
        accountId: UUID,
        amount: String,
    ): JsonNode = created(redeem(accountId, amount))

    fun json(
        path: String,
        accountId: UUID,
    ): JsonNode =
        objectMapper.readTree(
            get(path, accountId)
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    fun balance(accountId: UUID): String = json("/api/v1/me/balance", accountId)["balance"].asText()

    private fun created(result: ResultActionsDsl): JsonNode =
        objectMapper.readTree(
            result
                .andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString,
        )

    companion object {
        const val DEFAULT_PASSWORD = "s3nha-forte"
    }
}

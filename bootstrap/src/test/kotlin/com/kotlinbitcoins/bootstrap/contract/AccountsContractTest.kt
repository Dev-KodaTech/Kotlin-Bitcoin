package com.kotlinbitcoins.bootstrap.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import com.nimbusds.jwt.SignedJWT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** US1 acceptance scenarios 1–5 over the assembled application (contracts/openapi.yaml: /accounts, /auth/login). */
class AccountsContractTest : ApplicationTestBase() {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `scenario 1 - registration creates the account with zero balance and never returns the password`() {
        val body =
            register(ANA)
                .andExpect {
                    status { isCreated() }
                    content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                    jsonPath("$.id") { isNotEmpty() }
                    jsonPath("$.name") { value("Ana Souza") }
                    jsonPath("$.email") { value("ana@example.com") }
                    jsonPath("$.balance") { value("0.00") }
                    jsonPath("$.createdAt") { value("2026-11-09T08:10:00-03:00") }
                    jsonPath("$.password") { doesNotExist() }
                    jsonPath("$.passwordHash") { doesNotExist() }
                }.andReturn()
                .response.contentAsString

        assertThat(body).doesNotContain(PASSWORD)
        val stored = jdbcTemplate.queryForMap("SELECT email, password_hash, balance FROM accounts")
        assertThat(stored["email"]).isEqualTo("ana@example.com")
        assertThat(stored["password_hash"] as String).startsWith("\$2a\$12\$").doesNotContain(PASSWORD)
        assertThat(stored["balance"].toString()).isEqualTo("0.00")
    }

    @Test
    fun `scenario 2 - e-mail already registered, ignoring case and spaces, is 409 and creates nothing`() {
        register(ANA).andExpect { status { isCreated() } }

        register(account(email = "ANA@example.com "))
            .andExpectProblem(409, "EMAIL_ALREADY_IN_USE", "Este e-mail já está em uso.")

        assertThat(accountCount()).isEqualTo(1)
    }

    @Test
    fun `scenario 3 - invalid registration data is 400 listing the invalid fields`() {
        register(account(name = " ", email = "", password = ""))
            .andExpectProblem(400, "VALIDATION_ERROR", "Dados de entrada inválidos.")
            .andExpect {
                jsonPath("$.errors[?(@.field == 'name')].message") { isNotEmpty() }
                jsonPath("$.errors[?(@.field == 'email')].message") { isNotEmpty() }
                jsonPath("$.errors[?(@.field == 'password')].message") { isNotEmpty() }
            }
        register(account(email = "ana@example"))
            .andExpectProblem(400, "VALIDATION_ERROR", "Dados de entrada inválidos.")
            .andExpect {
                jsonPath("$.errors.length()") { value(1) }
                jsonPath("$.errors[0].field") { value("email") }
            }
        register(account(password = "1234567"))
            .andExpectProblem(400, "VALIDATION_ERROR", "Dados de entrada inválidos.")
            .andExpect { jsonPath("$.errors[0].field") { value("password") } }
        register(account(password = "é".repeat(37)))
            .andExpectProblem(400, "VALIDATION_ERROR", "Dados de entrada inválidos.")
            .andExpect { jsonPath("$.errors[0].field") { value("password") } }

        assertThat(accountCount()).isZero()
    }

    @Test
    fun `scenario 4 - correct credentials issue a bearer token for the account, valid for one hour`() {
        val accountId = idOf(register(ANA))

        val response =
            login(" ANA@example.com ", PASSWORD)
                .andExpect {
                    status { isOk() }
                    jsonPath("$.accessToken") { isNotEmpty() }
                    jsonPath("$.tokenType") { value("Bearer") }
                    jsonPath("$.expiresIn") { value(3600) }
                }.andReturn()
                .response.contentAsString
        val token = objectMapper.readTree(response)["accessToken"].asText()

        val claims = SignedJWT.parse(token).jwtClaimsSet
        assertThat(claims.subject).isEqualTo(accountId)
        assertThat(claims.expirationTime.toInstant()).isEqualTo(clock.now().plusSeconds(3600))
        assertThat(response).doesNotContain(PASSWORD)
        val protectedStatus =
            mockMvc
                .get("/api/v1/me/balance") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .andReturn()
                .response.status
        assertThat(protectedStatus).isNotEqualTo(401)
    }

    @Test
    fun `scenario 5 - wrong password and unknown e-mail get identical INVALID_CREDENTIALS answers`() {
        register(ANA).andExpect { status { isCreated() } }

        val wrongPassword =
            login("ana@example.com", "wrong-password")
                .andExpectProblem(401, "INVALID_CREDENTIALS", "E-mail ou senha inválidos.")
                .body()
        val unknownEmail =
            login("bia@example.com", PASSWORD)
                .andExpectProblem(401, "INVALID_CREDENTIALS", "E-mail ou senha inválidos.")
                .body()

        assertThat(withoutCorrelationId(unknownEmail)).isEqualTo(withoutCorrelationId(wrongPassword))
    }

    @Test
    fun `login with a password over 72 bytes is INVALID_CREDENTIALS, not an error`() {
        register(ANA).andExpect { status { isCreated() } }

        login("ana@example.com", "é".repeat(37))
            .andExpectProblem(401, "INVALID_CREDENTIALS", "E-mail ou senha inválidos.")
    }

    private fun register(json: String): ResultActionsDsl =
        mockMvc.post("/api/v1/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }

    private fun login(
        email: String,
        password: String,
    ): ResultActionsDsl =
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        }

    private fun account(
        name: String = "Ana Souza",
        email: String = "ana@example.com",
        password: String = PASSWORD,
    ): String = objectMapper.writeValueAsString(mapOf("name" to name, "email" to email, "password" to password))

    private fun idOf(result: ResultActionsDsl): String =
        objectMapper.readTree(result.andReturn().response.contentAsString)["id"].asText()

    private fun accountCount(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts", Int::class.java)!!

    private fun withoutCorrelationId(body: String): ObjectNode =
        (objectMapper.readTree(body) as ObjectNode).apply { remove("correlationId") }

    private fun ResultActionsDsl.andExpectProblem(
        status: Int,
        code: String,
        detail: String,
    ): ResultActionsDsl =
        andExpect {
            status { isEqualTo(status) }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("about:blank") }
            jsonPath("$.status") { value(status) }
            jsonPath("$.code") { value(code) }
            jsonPath("$.detail") { value(detail) }
            jsonPath("$.correlationId") { isNotEmpty() }
        }.also { assertThat(it.body()).doesNotContain(PASSWORD) }

    private fun ResultActionsDsl.body(): String = andReturn().response.contentAsString

    private companion object {
        const val PASSWORD = "s3nha-forte"
        const val ANA = """{"name":"Ana Souza","email":"ana@example.com","password":"$PASSWORD"}"""
    }
}

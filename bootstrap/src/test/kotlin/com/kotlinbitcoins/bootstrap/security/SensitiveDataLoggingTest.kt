package com.kotlinbitcoins.bootstrap.security

import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpHeaders
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.get

/**
 * FR-007: password, access token and `Authorization` header never show up in the logs or in the outbox payloads.
 * The request-detail logging of Spring MVC is switched on for this test (it prints headers and parameters), which is
 * the worst case the log masking has to withstand.
 */
@ExtendWith(OutputCaptureExtension::class)
@TestPropertySource(
    properties = [
        "spring.mvc.log-request-details=true",
        "logging.level.org.springframework.web=DEBUG",
        "logging.level.org.springframework.security=DEBUG",
    ],
)
class SensitiveDataLoggingTest : ApplicationTestBase() {
    @Test
    fun `registration, login and an authenticated call leave no secret in the logs`(output: CapturedOutput) {
        val ana = api.register("Ana", "ana@example.com", PASSWORD)
        val login =
            api
                .login(
                    "ana@example.com",
                    PASSWORD,
                ).andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val accessToken = jsonMapper.readTree(login)["accessToken"].asText()
        api.login("ana@example.com", "outra-$PASSWORD").andExpect { status { isUnauthorized() } }
        mockMvc
            .get("/api/v1/me/balance") { header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken") }
            .andExpect { status { isOk() } }
        api.depositOk(ana, "100.00")

        assertThat(output.all)
            .describedAs("logs were captured")
            .contains("\"message\"")
            .doesNotContain(PASSWORD)
            .doesNotContain("outra-$PASSWORD")
            .doesNotContain(accessToken)
            .doesNotContain(accessToken.substringAfter('.').substringBefore('.')) // the JWT payload
            .doesNotContainPattern("Bearer\\s+[A-Za-z0-9_-]{10,}")
    }

    @Test
    fun `the outbox payload holds no password or token`() {
        val ana = api.register("Ana", "ana@example.com", PASSWORD)
        val accessToken =
            jsonMapper
                .readTree(
                    api
                        .login("ana@example.com", PASSWORD)
                        .andReturn()
                        .response.contentAsString,
                )["accessToken"]
                .asText()
        api.depositOk(ana, "100.00")

        val payloads = jdbcTemplate.queryForList("SELECT payload::text FROM outbox_events", String::class.java)

        assertThat(payloads).isNotEmpty()
        payloads.forEach { payload ->
            assertThat(payload).doesNotContain(PASSWORD).doesNotContain(accessToken).doesNotContain("Bearer")
            assertThat(payload.lowercase()).doesNotContain("password").doesNotContain("token")
        }
    }

    private companion object {
        const val PASSWORD = "uma-senha-MUITO-secreta-4711"
    }
}

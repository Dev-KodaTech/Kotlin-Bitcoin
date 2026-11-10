package com.kotlinbitcoins.api.error

import com.kotlinbitcoins.api.web.CorrelationIdFilter
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@WebMvcTest(controllers = [ApiExceptionHandlerTest.TestController::class])
@Import(
    ApiExceptionHandlerTest.TestController::class,
    ApiExceptionHandlerTest.PermitAllSecurity::class,
    ProblemDetailsMapper::class,
)
class ApiExceptionHandlerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `malformed JSON becomes VALIDATION_ERROR`() {
        mockMvc
            .perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
            .andExpectProblem(status = 400, code = "VALIDATION_ERROR")
    }

    @Test
    fun `bean validation failure becomes VALIDATION_ERROR with field errors`() {
        mockMvc
            .perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("""{"name":"","code":"x"}"""))
            .andExpectProblem(status = 400, code = "VALIDATION_ERROR")
            .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("Nome é obrigatório."))
            .andExpect(jsonPath("$.errors[?(@.field == 'code')].message").value("Código deve ter 3 caracteres."))
    }

    @Test
    fun `unexpected exception becomes a generic 500 without internal details`() {
        mockMvc
            .perform(get("/test/boom"))
            .andExpect(status().isInternalServerError)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.detail").value(ProblemCode.INTERNAL_ERROR.defaultDetail))
            .andExpect(jsonPath("$.correlationId").isNotEmpty)
            .andExpect(content().string(not(containsString("secret-internal-message"))))
            .andExpect(content().string(not(containsString("IllegalStateException"))))
            .andExpect(content().string(not(containsString("TestController"))))
            .andExpect(jsonPath("$.trace").doesNotExist())
    }

    @Test
    fun `unknown route keeps 404 as Problem Details`() {
        mockMvc
            .perform(get("/test/does-not-exist"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.correlationId").isNotEmpty)
    }

    @Test
    fun `errors carry the correlation id of the request`() {
        mockMvc
            .perform(
                post("/test/body")
                    .header(CorrelationIdFilter.HEADER, "abc-123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{"),
            ).andExpect(jsonPath("$.correlationId").value("abc-123"))
            .andExpect(header().string(CorrelationIdFilter.HEADER, "abc-123"))
    }

    private fun ResultActions.andExpectProblem(
        status: Int,
        code: String,
    ): ResultActions =
        andExpect(status().`is`(status))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").isNotEmpty)
            .andExpect(jsonPath("$.status").value(status))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.detail").value("Dados de entrada inválidos."))
            .andExpect(jsonPath("$.correlationId").isNotEmpty)

    data class TestBody(
        @field:NotBlank(message = "Nome é obrigatório.")
        val name: String?,
        @field:Size(min = 3, max = 3, message = "Código deve ter 3 caracteres.")
        val code: String?,
    )

    @RestController
    class TestController {
        @PostMapping("/test/body")
        fun body(
            @Valid @RequestBody body: TestBody,
        ): String = body.name.orEmpty()

        @GetMapping("/test/boom")
        fun boom(): String = error("secret-internal-message")
    }

    @TestConfiguration
    class PermitAllSecurity {
        @Bean
        fun permitAll(http: HttpSecurity): SecurityFilterChain =
            http
                .csrf { it.disable() }
                .authorizeHttpRequests { it.anyRequest().permitAll() }
                .build()
    }
}

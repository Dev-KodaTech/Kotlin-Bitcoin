package com.kotlinbitcoins.api.web

import com.kotlinbitcoins.domain.shared.AccountId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

class CurrentAccountArgumentResolverTest {
    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(TestController())
            .setCustomArgumentResolvers(CurrentAccountArgumentResolver())
            .build()

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    @Test
    fun `resolves the AccountId from the sub claim of the authenticated JWT`() {
        val accountId = UUID.randomUUID()
        authenticateAs(accountId.toString())

        mockMvc
            .perform(get("/test/me"))
            .andExpect(content().string(accountId.toString()))
    }

    private fun authenticateAs(subject: String) {
        val jwt =
            Jwt
                .withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    @RestController
    class TestController {
        @GetMapping("/test/me")
        fun me(
            @CurrentAccount accountId: AccountId,
        ): String = accountId.value.toString()
    }
}

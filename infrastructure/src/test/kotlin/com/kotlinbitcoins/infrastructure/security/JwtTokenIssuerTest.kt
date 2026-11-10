package com.kotlinbitcoins.infrastructure.security

import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.domain.shared.AccountId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.spec.SecretKeySpec

class JwtTokenIssuerTest {
    private val key = SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256")
    private val issuer = JwtTokenIssuer(key, Clock { NOW }, Duration.ofHours(1))

    private val decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().apply {
            setJwtValidator(
                JwtTimestampValidator().apply { setClock(java.time.Clock.fixed(NOW, ZoneOffset.UTC)) },
            )
        }

    @Test
    fun `issues an HS256 token with the account as subject, valid for one hour from the clock`() {
        val accountId = AccountId.random()

        val token = issuer.issue(accountId)
        val jwt = decoder.decode(token.value)

        assertThat(jwt.headers["alg"]).isEqualTo("HS256")
        assertThat(jwt.subject).isEqualTo(accountId.toString())
        assertThat(jwt.issuedAt).isEqualTo(NOW)
        assertThat(jwt.expiresAt).isEqualTo(NOW.plus(Duration.ofHours(1)))
        assertThat(token.expiresInSeconds).isEqualTo(3600)
    }

    @Test
    fun `the token is rejected when verified with another secret`() {
        val otherKey = SecretKeySpec("another-secret-with-at-least-32-bytes!!".toByteArray(), "HmacSHA256")
        val otherDecoder = NimbusJwtDecoder.withSecretKey(otherKey).macAlgorithm(MacAlgorithm.HS256).build()

        val token = issuer.issue(AccountId.random())

        assertThat(runCatching { otherDecoder.decode(token.value) }.isFailure).isTrue()
    }

    @Test
    fun `the access token never prints its value`() {
        val token = issuer.issue(AccountId.random())

        assertThat(token.toString()).doesNotContain(token.value)
    }

    private companion object {
        const val SECRET = "test-only-jwt-secret-with-at-least-32-bytes!"
        val NOW: Instant = Instant.parse("2026-11-09T11:10:00Z")
    }
}

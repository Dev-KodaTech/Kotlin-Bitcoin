package com.kotlinbitcoins.bootstrap.support

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

/**
 * Tokens for security tests, signed with the same test secret the application receives as `app.jwt.secret`.
 * Times are relative to the given instant (normally the [MutableClock] time, which the decoder also uses).
 */
object JwtTestTokens {
    /** Test-only secret (≥ 32 bytes). Never used outside automated tests. */
    const val SECRET = "test-only-jwt-secret-with-at-least-32-bytes!"

    private const val OTHER_SECRET = "a-different-secret-that-the-app-does-not-know"

    fun valid(
        accountId: UUID,
        now: Instant,
    ): String = sign(SECRET, accountId, issuedAt = now, expiresAt = now.plus(Duration.ofHours(1)))

    fun expired(
        accountId: UUID,
        now: Instant,
    ): String =
        sign(SECRET, accountId, issuedAt = now.minus(Duration.ofHours(2)), expiresAt = now.minus(Duration.ofHours(1)))

    /** Well-formed token whose signature was made with another key. */
    fun tampered(
        accountId: UUID,
        now: Instant,
    ): String = sign(OTHER_SECRET, accountId, issuedAt = now, expiresAt = now.plus(Duration.ofHours(1)))

    /** Not a JWT at all. */
    const val MALFORMED = "abc"

    private fun sign(
        secret: String,
        accountId: UUID,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String {
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val encoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(key))
        val claims =
            JwtClaimsSet
                .builder()
                .subject(accountId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }
}

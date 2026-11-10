package com.kotlinbitcoins.infrastructure.security

import com.kotlinbitcoins.application.port.AccessToken
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.TokenIssuer
import com.kotlinbitcoins.domain.shared.AccountId
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Duration
import javax.crypto.SecretKey

/**
 * Issues HS256 JWTs signed with `JWT_SECRET` (R-09): `sub` = account id, `iat` = now and `exp` = now + [ttl],
 * both from the [Clock] port. There is no refresh token.
 */
class JwtTokenIssuer(
    secretKey: SecretKey,
    private val clock: Clock,
    private val ttl: Duration,
) : TokenIssuer {
    private val encoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(secretKey))

    override fun issue(accountId: AccountId): AccessToken {
        val issuedAt = clock.now()
        val claims =
            JwtClaimsSet
                .builder()
                .subject(accountId.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = encoder.encode(JwtEncoderParameters.from(header, claims))
        return AccessToken(token.tokenValue, ttl.seconds)
    }
}

package com.kotlinbitcoins.bootstrap

import com.fasterxml.jackson.databind.ObjectMapper
import com.kotlinbitcoins.api.error.ProblemCode
import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.web.CurrentAccountArgumentResolver
import com.kotlinbitcoins.application.port.Clock
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Stateless JWT (HS256) resource server (R-09). Public routes: registration, login, health probes and API docs.
 * Every authentication failure (missing, malformed, tampered or expired token) answers Problem Details
 * `UNAUTHORIZED`. Token expiry is checked against the [Clock] port, like every other time-based rule.
 */
@Configuration
@EnableWebSecurity
class SecurityConfiguration : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(CurrentAccountArgumentResolver())
    }

    @Bean
    fun jwtSecretKey(
        @Value("\${app.jwt.secret}") secret: String,
    ): SecretKey {
        val bytes = secret.toByteArray(Charsets.UTF_8)
        require(bytes.size >= MIN_SECRET_BYTES) { "app.jwt.secret (JWT_SECRET) must have at least 32 bytes" }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    @Bean
    fun jwtDecoder(
        jwtSecretKey: SecretKey,
        clock: Clock,
    ): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build().apply {
            setJwtValidator(JwtTimestampValidator(CLOCK_SKEW).apply { setClock(clock.asJavaClock()) })
        }

    @Bean
    fun problemDetailsAuthenticationEntryPoint(
        problems: ProblemDetailsMapper,
        objectMapper: ObjectMapper,
    ): AuthenticationEntryPoint = ProblemDetailsAuthenticationEntryPoint(problems, objectMapper)

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        problemDetailsAuthenticationEntryPoint: AuthenticationEntryPoint,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(HttpMethod.POST, "/api/v1/accounts", "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer {
                it.jwt { }
                it.authenticationEntryPoint(problemDetailsAuthenticationEntryPoint)
            }.exceptionHandling { it.authenticationEntryPoint(problemDetailsAuthenticationEntryPoint) }
            .build()

    private companion object {
        const val MIN_SECRET_BYTES = 32
        val CLOCK_SKEW: Duration = Duration.ofSeconds(60)
    }
}

/** Writes `UNAUTHORIZED` as `application/problem+json`, with the `WWW-Authenticate: Bearer` challenge. */
private class ProblemDetailsAuthenticationEntryPoint(
    private val problems: ProblemDetailsMapper,
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val problem = problems.problem(ProblemCode.UNAUTHORIZED)
        response.status = ProblemCode.UNAUTHORIZED.status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        objectMapper.writeValue(response.outputStream, problem)
    }
}

/** Exposes the [Clock] port as a `java.time.Clock` for libraries that need one. */
private fun Clock.asJavaClock(): java.time.Clock = PortBackedJavaClock(this, ZoneOffset.UTC)

private class PortBackedJavaClock(
    private val clock: Clock,
    private val zone: ZoneId,
) : java.time.Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): java.time.Clock = PortBackedJavaClock(clock, zone)

    override fun instant(): Instant = clock.now()
}

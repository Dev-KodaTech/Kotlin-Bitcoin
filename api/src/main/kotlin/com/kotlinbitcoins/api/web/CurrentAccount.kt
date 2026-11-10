package com.kotlinbitcoins.api.web

import com.kotlinbitcoins.domain.shared.AccountId
import org.springframework.core.MethodParameter
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

/**
 * Marks a controller parameter that receives the authenticated account (`sub` of the JWT). Protected routes never
 * take an account id from the client (FR-033).
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentAccount

/**
 * Resolves [CurrentAccount] parameters. A non-null [AccountId] parameter is an inline class, so on the JVM it is a
 * [UUID]; Spring boxes it back into [AccountId] when invoking the Kotlin function.
 */
class CurrentAccountArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentAccount::class.java) &&
            parameter.parameterType in SUPPORTED_TYPES

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val authentication =
            SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
                ?: throw AuthenticationCredentialsNotFoundException("No authenticated JWT")
        val accountId =
            runCatching { UUID.fromString(authentication.token.subject) }.getOrNull()
                ?: throw BadCredentialsException("JWT subject is not an account id")
        return if (parameter.parameterType == AccountId::class.java) AccountId(accountId) else accountId
    }

    private companion object {
        val SUPPORTED_TYPES = setOf(UUID::class.java, AccountId::class.java)
    }
}

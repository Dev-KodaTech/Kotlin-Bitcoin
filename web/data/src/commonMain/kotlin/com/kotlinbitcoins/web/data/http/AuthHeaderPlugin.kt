package com.kotlinbitcoins.web.data.http

import com.kotlinbitcoins.web.state.port.SessionTokenProvider
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.encodedPath

class AuthHeaderConfig {
    var tokenProvider: SessionTokenProvider? = null
}

/** Thrown before sending when an authenticated request has no valid session; [ApiCall] turns it into a value. */
internal class SessionUnavailableException : IllegalStateException("No valid session for an authenticated request")

/** Routes of the contract that do not take a token (registration and login). */
private val PUBLIC_PATH_SUFFIXES = listOf("/accounts", "/auth/login")

/**
 * Adds `Authorization: Bearer <token>` to every authenticated request (W-05), reading the token at send time from
 * [SessionTokenProvider]. Without a valid session the request is not sent. The token never goes to the URL.
 */
val AuthHeaderPlugin =
    createClientPlugin("AuthHeader", ::AuthHeaderConfig) {
        val tokenProvider = requireNotNull(pluginConfig.tokenProvider) { "AuthHeaderPlugin needs a tokenProvider" }
        onRequest { request, _ ->
            val path = request.url.encodedPath.trimEnd('/')
            if (PUBLIC_PATH_SUFFIXES.none { path.endsWith(it) }) {
                val token = tokenProvider.tokenForRequest() ?: throw SessionUnavailableException()
                request.headers[HttpHeaders.Authorization] = "Bearer $token"
            }
        }
    }

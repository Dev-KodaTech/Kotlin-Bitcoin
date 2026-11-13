package com.kotlinbitcoins.web.data.http

import com.kotlinbitcoins.web.state.port.SessionTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The HTTP client of the app (W-05). No `Logging` plugin (FR-008): the token and the bodies never reach the console.
 */
object HttpClientFactory {
    /** Aligned with `proxy_read_timeout 30s` of the nginx. */
    private const val REQUEST_TIMEOUT_MS = 30_000L

    val json: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    /** [baseUrl] ends with `/` (e.g. `https://host/api/v1/`) so that request paths are relative to it. */
    fun create(
        baseUrl: String,
        tokenProvider: SessionTokenProvider,
        engine: HttpClientEngine? = null,
    ): HttpClient {
        val configure: HttpClientConfig<*>.() -> Unit = {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
            install(AuthHeaderPlugin) { this.tokenProvider = tokenProvider }
            defaultRequest { url(baseUrl) }
        }
        return if (engine != null) HttpClient(engine, configure) else HttpClient(Js, configure)
    }
}

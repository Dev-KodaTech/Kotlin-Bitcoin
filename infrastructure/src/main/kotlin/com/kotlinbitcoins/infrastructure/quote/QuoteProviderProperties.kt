package com.kotlinbitcoins.infrastructure.quote

import java.time.Duration

/**
 * Settings of the quote source (R-06): `quote.provider.url` plus the connect and read timeouts. The source is slow
 * or down often enough that waiting is never an option, so the defaults are short.
 */
data class QuoteProviderProperties(
    val url: String,
    val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    val readTimeout: Duration = DEFAULT_READ_TIMEOUT,
) {
    companion object {
        val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(1)
        val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(2)
    }
}

package com.kotlinbitcoins.web.state.session

import kotlin.time.Instant

/** An authenticated session. The token never appears in `toString()` (FR-008). */
data class Session(
    val accessToken: String,
    val expiresAt: Instant,
) {
    override fun toString(): String = "Session(****)"
}

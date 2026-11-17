package com.kotlinbitcoins.web.state.session

/** Messages of the `kb-session` channel between tabs (data-model §8). */
sealed interface TabMessage {
    /** A tab without session asks the others. */
    data object Request : TabMessage

    /** A tab with a valid session (or that just logged in) shares it. */
    data class Share(
        val accessToken: String,
        val expiresAtEpochMs: Long,
    ) : TabMessage {
        override fun toString(): String = "Share(****, expiresAtEpochMs=$expiresAtEpochMs)"
    }

    data object Logout : TabMessage

    data object Expired : TabMessage
}

package com.kotlinbitcoins.web.state.port

/** Per-tab storage of the session (`sessionStorage`, W-08). The token never goes to URL, cookie or log (FR-008). */
interface SessionStorage {
    fun read(): StoredSession?

    fun write(session: StoredSession)

    fun clear()
}

data class StoredSession(
    val accessToken: String,
    val expiresAtEpochMs: Long,
) {
    override fun toString(): String = "StoredSession(****, expiresAtEpochMs=$expiresAtEpochMs)"
}

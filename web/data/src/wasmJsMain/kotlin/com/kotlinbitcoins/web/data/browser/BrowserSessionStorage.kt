package com.kotlinbitcoins.web.data.browser

import com.kotlinbitcoins.web.state.port.SessionStorage
import com.kotlinbitcoins.web.state.port.StoredSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `sessionStorage` under the key `kb.session` (W-08): survives a reload and ends with the tab. Nothing is logged.
 * A blocked or full storage behaves as an empty one; the session then lives only in memory.
 */
class BrowserSessionStorage : SessionStorage {
    @Serializable
    private data class Stored(
        val accessToken: String,
        val expiresAtEpochMs: Long,
    ) {
        override fun toString(): String = "Stored(****)"
    }

    override fun read(): StoredSession? =
        guarded(fallback = null) {
            sessionStorageGet(KEY)
                ?.let { json.decodeFromString(Stored.serializer(), it) }
                ?.let { StoredSession(it.accessToken, it.expiresAtEpochMs) }
        }

    override fun write(session: StoredSession) {
        guarded(fallback = Unit) {
            sessionStorageSet(
                KEY,
                json.encodeToString(Stored.serializer(), Stored(session.accessToken, session.expiresAtEpochMs)),
            )
        }
    }

    override fun clear() {
        guarded(fallback = Unit) { sessionStorageRemove(KEY) }
    }

    private companion object {
        const val KEY = "kb.session"
        val json = Json { ignoreUnknownKeys = true }
    }
}

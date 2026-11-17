package com.kotlinbitcoins.web.state.support

import com.kotlinbitcoins.web.state.port.SessionStorage
import com.kotlinbitcoins.web.state.port.StoredSession

class FakeSessionStorage(
    var stored: StoredSession? = null,
) : SessionStorage {
    override fun read(): StoredSession? = stored

    override fun write(session: StoredSession) {
        stored = session
    }

    override fun clear() {
        stored = null
    }
}

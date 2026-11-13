@file:OptIn(ExperimentalWasmJsInterop::class)

package com.kotlinbitcoins.web.data.browser

import com.kotlinbitcoins.web.data.session.TabMessageCodec
import com.kotlinbitcoins.web.state.port.TabChannel
import com.kotlinbitcoins.web.state.session.TabMessage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * `BroadcastChannel("kb-session")` between tabs of the same origin (W-08). Messages are JSON strings of
 * [TabMessageCodec]; malformed ones are dropped without logging their content.
 */
class BroadcastTabChannel : TabChannel {
    private val broadcast = BroadcastChannel(CHANNEL_NAME)

    override val messages: Flow<TabMessage> =
        callbackFlow {
            val listener = addMessageListener(broadcast) { text -> TabMessageCodec.decode(text)?.let(::trySend) }
            awaitClose { removeMessageListener(broadcast, listener) }
        }

    override fun send(message: TabMessage) {
        guarded(fallback = Unit) { broadcast.postMessage(TabMessageCodec.encode(message)) }
    }

    /** Called when the page goes away. */
    fun close() {
        guarded(fallback = Unit) { broadcast.close() }
    }

    private companion object {
        const val CHANNEL_NAME = "kb-session"
    }
}

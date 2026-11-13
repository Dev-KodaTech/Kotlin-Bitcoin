package com.kotlinbitcoins.web.state.support

import com.kotlinbitcoins.web.state.port.TabChannel
import com.kotlinbitcoins.web.state.session.TabMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Connected tabs of the same origin: like a BroadcastChannel, a message reaches every tab except the sender. */
class FakeTabNetwork {
    private val tabs = mutableListOf<FakeTabChannel>()

    fun newTab(): FakeTabChannel = FakeTabChannel(this).also(tabs::add)

    internal fun deliver(
        from: FakeTabChannel,
        message: TabMessage,
    ) {
        tabs.filter { it !== from }.forEach { it.receive(message) }
    }
}

class FakeTabChannel internal constructor(
    private val network: FakeTabNetwork,
) : TabChannel {
    private val incoming = MutableSharedFlow<TabMessage>(extraBufferCapacity = 64)

    /** Messages this tab sent, in order. */
    val sent = mutableListOf<TabMessage>()

    override val messages: Flow<TabMessage> = incoming

    override fun send(message: TabMessage) {
        sent += message
        network.deliver(this, message)
    }

    internal fun receive(message: TabMessage) {
        incoming.tryEmit(message)
    }
}

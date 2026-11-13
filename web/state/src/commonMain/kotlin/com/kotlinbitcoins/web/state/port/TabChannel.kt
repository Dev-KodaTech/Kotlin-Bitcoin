package com.kotlinbitcoins.web.state.port

import com.kotlinbitcoins.web.state.session.TabMessage
import kotlinx.coroutines.flow.Flow

/** Messages between tabs of the same origin (`BroadcastChannel("kb-session")`, W-08). The sender does not receive. */
interface TabChannel {
    val messages: Flow<TabMessage>

    fun send(message: TabMessage)
}

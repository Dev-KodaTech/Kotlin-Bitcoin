package com.kotlinbitcoins.web.data.session

import com.kotlinbitcoins.web.state.session.TabMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** data-model §8. */
class TabMessageCodecTest {
    @Test
    fun roundTrip() {
        listOf(
            TabMessage.Request,
            TabMessage.Share("token-a", 1_794_221_400_000),
            TabMessage.Logout,
            TabMessage.Expired,
        ).forEach { message -> assertEquals(message, TabMessageCodec.decode(TabMessageCodec.encode(message))) }
    }

    @Test
    fun wireFormatUsesTheTypeDiscriminator() {
        assertEquals("""{"type":"request"}""", TabMessageCodec.encode(TabMessage.Request))
        assertEquals("""{"type":"logout"}""", TabMessageCodec.encode(TabMessage.Logout))
        assertEquals("""{"type":"expired"}""", TabMessageCodec.encode(TabMessage.Expired))
        assertEquals(
            TabMessage.Share("token-a", 42),
            TabMessageCodec.decode("""{"type":"share","accessToken":"token-a","expiresAtEpochMs":42}"""),
        )
    }

    @Test
    fun malformedOrUnknownMessagesAreIgnored() {
        listOf(
            "",
            "not json",
            "{",
            "[]",
            "null",
            """{"type":"hello"}""",
            """{"kind":"request"}""",
            """{"type":"share"}""",
            """{"type":"share","accessToken":"token-a","expiresAtEpochMs":"soon"}""",
        ).forEach { assertNull(TabMessageCodec.decode(it), it) }
    }
}

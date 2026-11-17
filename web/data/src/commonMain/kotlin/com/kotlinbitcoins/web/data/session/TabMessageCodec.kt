package com.kotlinbitcoins.web.data.session

import com.kotlinbitcoins.web.state.session.TabMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** JSON of the `kb-session` channel, with the `type` discriminator (data-model §8). */
object TabMessageCodec {
    private val json = Json { classDiscriminator = "type" }

    fun encode(message: TabMessage): String = json.encodeToString(Wire.serializer(), Wire.of(message))

    /** Malformed JSON or an unknown `type` is ignored (null), and its content is never logged. */
    fun decode(text: String): TabMessage? =
        try {
            json.decodeFromString(Wire.serializer(), text).toMessage()
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    @Serializable
    private sealed interface Wire {
        fun toMessage(): TabMessage

        @Serializable
        @SerialName("request")
        data object Request : Wire {
            override fun toMessage() = TabMessage.Request
        }

        @Serializable
        @SerialName("share")
        data class Share(
            val accessToken: String,
            val expiresAtEpochMs: Long,
        ) : Wire {
            override fun toMessage() = TabMessage.Share(accessToken, expiresAtEpochMs)

            override fun toString(): String = "Share(****)"
        }

        @Serializable
        @SerialName("logout")
        data object Logout : Wire {
            override fun toMessage() = TabMessage.Logout
        }

        @Serializable
        @SerialName("expired")
        data object Expired : Wire {
            override fun toMessage() = TabMessage.Expired
        }

        companion object {
            fun of(message: TabMessage): Wire =
                when (message) {
                    TabMessage.Request -> Request
                    is TabMessage.Share -> Share(message.accessToken, message.expiresAtEpochMs)
                    TabMessage.Logout -> Logout
                    TabMessage.Expired -> Expired
                }
        }
    }
}

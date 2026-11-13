package com.kotlinbitcoins.web.core.navigation

/** Percent-encoding of URI components (RFC 3986), in pure Kotlin so that :core does not depend on the browser. */
internal object UriComponent {
    private const val HEX = "0123456789ABCDEF"
    private const val BYTE_MASK = 0xFF
    private const val HIGH_NIBBLE_SHIFT = 4
    private const val LOW_NIBBLE_MASK = 0x0F
    private const val HEX_RADIX = 16
    private const val ESCAPE_LENGTH = 3

    fun encode(value: String): String =
        buildString {
            value.encodeToByteArray().forEach { byte ->
                val code = byte.toInt() and BYTE_MASK
                val char = code.toChar()
                if (code < BYTE_MASK && isUnreserved(char)) {
                    append(char)
                } else {
                    append('%')
                    append(HEX[code shr HIGH_NIBBLE_SHIFT])
                    append(HEX[code and LOW_NIBBLE_MASK])
                }
            }
        }

    /** Decodes once; returns null on a malformed escape. */
    fun decode(value: String): String? {
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%') {
                val hex = value.substring(index + 1, minOf(index + ESCAPE_LENGTH, value.length))
                val code = hex.takeIf { it.length == 2 }?.toIntOrNull(HEX_RADIX) ?: return null
                bytes.add(code.toByte())
                index += ESCAPE_LENGTH
            } else {
                char.toString().encodeToByteArray().forEach(bytes::add)
                index++
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun isUnreserved(char: Char): Boolean =
        char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == '-' || char == '_' || char == '.' ||
            char == '~'
}

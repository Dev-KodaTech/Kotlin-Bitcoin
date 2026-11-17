package com.kotlinbitcoins.web.core.money

/**
 * pt-BR number formatting over a plain decimal string (FR-036, W-11): thousands grouped with `.` and decimal comma.
 * No `Intl`, no `String.format`, no floating point.
 */
internal object PtBrNumberFormat {
    private const val GROUP_SIZE = 3

    /** "1234567.89" → "1.234.567,89"; "-5.00" → "-5,00" (the caller chooses the sign symbol). */
    fun format(plain: String): String {
        val negative = plain.startsWith('-')
        val unsigned = plain.removePrefix("-")
        val integerPart = unsigned.substringBefore('.')
        val fraction = unsigned.substringAfter('.', missingDelimiterValue = "")
        val grouped =
            integerPart
                .reversed()
                .chunked(GROUP_SIZE)
                .joinToString(".")
                .reversed()
        val body = if (fraction.isEmpty()) grouped else "$grouped,$fraction"
        return if (negative) "-$body" else body
    }
}

/** Symbol and separator of a BRL amount: "R$" followed by a non-breaking space (U+00A0). */
internal const val BRL_PREFIX = "R$ "

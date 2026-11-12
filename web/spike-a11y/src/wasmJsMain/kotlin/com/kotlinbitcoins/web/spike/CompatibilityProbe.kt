package com.kotlinbitcoins.web.spike

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val BTC_SCALE = 8L
private const val PRECISION = 30L

private val brasiliaFormat =
    LocalDateTime.Format {
        day()
        char('/')
        monthNumber()
        char('/')
        year()
        char(' ')
        hour()
        char(':')
        minute()
    }

/**
 * T014: proves at runtime that bignum 0.3.10 (W-06) and kotlinx-datetime with `@js-joda/timezone` (W-07) work on
 * Kotlin 2.4.21 / wasmJs. Expected: "0.00100000 BTC · 09/11/2026 08:10".
 */
fun compatibilityProbe(): String {
    val btc =
        BigDecimal
            .parseString("400.00")
            .divide(BigDecimal.parseString("400000.00"), DecimalMode(PRECISION, RoundingMode.FLOOR, BTC_SCALE))
            .toPlainString()
    val brasilia = TimeZone.of("America/Sao_Paulo")
    val dateTime = brasiliaFormat.format(Instant.parse("2026-11-09T11:10:00Z").toLocalDateTime(brasilia))
    return "Sonda de compatibilidade: $btc BTC · $dateTime"
}

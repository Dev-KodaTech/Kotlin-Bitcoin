package com.kotlinbitcoins.api.format

import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.QuotePrice
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Output formats of the API (R-10): amounts as strings with fixed scale and instants as ISO-8601 with the offset
 * of the business zone, `America/Sao_Paulo` (FR-034a), to the second.
 */
@Component
class ApiFormats(
    private val businessZone: ZoneId,
) {
    fun money(value: Money): String = value.amount.toPlainString()

    fun btc(value: BtcAmount): String = value.toBtcDecimal().toPlainString()

    fun quote(value: QuotePrice): String = value.value.toPlainString()

    fun instant(value: Instant): String = INSTANT_FORMAT.format(value.atZone(businessZone))

    private companion object {
        val INSTANT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    }
}

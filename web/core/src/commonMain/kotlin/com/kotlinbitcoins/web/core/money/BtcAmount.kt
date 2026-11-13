package com.kotlinbitcoins.web.core.money

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome

/** An amount of bitcoin: ≥ 0 with exactly 8 decimals (data-model §1). */
class BtcAmount private constructor(
    private val plain: String,
) : Comparable<BtcAmount> {
    fun toApiString(): String = plain

    /** "0,00100000 BTC", always with 8 decimals (FR-036). */
    fun format(): String = PtBrNumberFormat.format(plain) + " BTC"

    operator fun plus(other: BtcAmount): BtcAmount = BtcAmount(DecimalSupport.add(plain, other.plain, SCALE))

    /** True when the amount is below 0.00000001 BTC, which with 8 exact decimals means zero (FR-016). */
    fun isBelowOneSatoshi(): Boolean = DecimalSupport.isZero(plain)

    override fun compareTo(other: BtcAmount): Int = DecimalSupport.compare(plain, other.plain)

    override fun equals(other: Any?): Boolean = other is BtcAmount && other.plain == plain

    override fun hashCode(): Int = plain.hashCode()

    override fun toString(): String = "BtcAmount($plain)"

    companion object {
        internal const val SCALE = 8
        private val API_PATTERN = Regex("""^\d+\.\d{8}$""")

        fun parseApi(raw: String): Outcome<ApiError, BtcAmount> =
            if (API_PATTERN.matches(raw)) {
                Outcome.Success(BtcAmount(DecimalSupport.withScale(raw, SCALE)))
            } else {
                Outcome.Failure(ApiError.Unexpected)
            }

        /** For results of the exact division of the estimates (already truncated to 8 decimals). */
        internal fun ofValidated(plain: String): BtcAmount = BtcAmount(DecimalSupport.withScale(plain, SCALE))
    }
}

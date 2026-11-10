package com.kotlinbitcoins.application.investment

import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvestmentId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.QuotePrice
import java.math.BigDecimal
import java.time.Instant

/** One active investment as shown in the position (FR-016). [variationPercent] has scale 2. */
data class PositionItem(
    val investmentId: InvestmentId,
    val purchasedAt: Instant,
    val investedAmount: Money,
    val purchaseQuote: QuotePrice,
    val btcAmount: BtcAmount,
    val variationPercent: BigDecimal,
    val currentValue: Money,
)

/**
 * Read model of US6: the quote used, the active investments in FIFO order and the total current value, which is the
 * sum of each item's value already rounded down to the cent (FR-022).
 */
data class InvestmentPosition(
    val quote: Quote,
    val totalCurrentValue: Money,
    val items: List<PositionItem>,
)

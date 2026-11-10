package com.kotlinbitcoins.application.investment

import com.kotlinbitcoins.application.port.InfrastructureUnavailable
import com.kotlinbitcoins.application.port.InvestmentRepository
import com.kotlinbitcoins.application.quote.QuoteService
import com.kotlinbitcoins.domain.investment.BtcConversion
import com.kotlinbitcoins.domain.investment.Investment
import com.kotlinbitcoins.domain.quote.PriceVariation
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.flatMap
import com.kotlinbitcoins.domain.shared.map
import com.kotlinbitcoins.domain.shared.mapError

sealed interface GetInvestmentPositionError {
    /** No quote from the source and none of 60 s or less in the cache (FR-012). */
    data object QuoteUnavailable : GetInvestmentPositionError

    data object ServiceUnavailable : GetInvestmentPositionError, InfrastructureUnavailable
}

/**
 * Lists the active investments of the account with the variation and the gross value at the current buy price
 * (US6, FR-016, FR-017). A single quote is used for every item, so the position is consistent. A read has no side
 * effect, so it needs neither a transaction nor the account lock.
 */
class GetInvestmentPosition(
    private val investments: InvestmentRepository,
    private val quoteService: QuoteService,
) {
    fun execute(accountId: AccountId): Outcome<GetInvestmentPositionError, InvestmentPosition> =
        investments
            .findActiveByAccountInFifoOrder(accountId)
            .mapError<_, _, GetInvestmentPositionError> { GetInvestmentPositionError.ServiceUnavailable }
            .flatMap { active ->
                quoteService
                    .currentQuote()
                    .mapError<_, _, GetInvestmentPositionError> { GetInvestmentPositionError.QuoteUnavailable }
                    .map { quote -> position(quote, active) }
            }

    private fun position(
        quote: Quote,
        active: List<Investment>,
    ): InvestmentPosition {
        val items = active.map { item(it, quote) }
        val total = items.fold(Money.ZERO) { sum, item -> sum + item.currentValue }
        return InvestmentPosition(quote, total, items)
    }

    private fun item(
        investment: Investment,
        quote: Quote,
    ) = PositionItem(
        investmentId = investment.id,
        purchasedAt = investment.purchasedAt,
        investedAmount = investment.investedAmount,
        purchaseQuote = investment.purchaseQuote,
        btcAmount = investment.btcAmount,
        variationPercent = PriceVariation.percent(investment.purchaseQuote, quote.buyPrice),
        currentValue = BtcConversion.currentValue(investment.btcAmount, quote.buyPrice),
    )
}

package com.kotlinbitcoins.web.data.mapper

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.error.flatMap
import com.kotlinbitcoins.web.core.error.map
import com.kotlinbitcoins.web.core.model.InvestmentPosition
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.money.VariationPercent
import com.kotlinbitcoins.web.core.time.ApiInstant
import com.kotlinbitcoins.web.data.dto.InvestmentPositionDto
import com.kotlinbitcoins.web.data.dto.PositionDto

/**
 * [PositionDto] → [Position], keeping the order of the API (FR-019). Every value is taken as received (Principle II);
 * any value outside the contract pattern makes the whole position [ApiError.Unexpected] (FR-041).
 */
object PositionMapper {
    fun toPosition(dto: PositionDto): Outcome<ApiError, Position> =
        QuoteMapper.toQuote(dto.quote).flatMap { quote ->
            Money.parseApi(dto.totalCurrentValue).flatMap { total ->
                investments(dto.investments).map { Position(quote, total, it) }
            }
        }

    private fun investments(dtos: List<InvestmentPositionDto>): Outcome<ApiError, List<InvestmentPosition>> {
        val mapped = ArrayList<InvestmentPosition>(dtos.size)
        for (dto in dtos) {
            when (val investment = toInvestment(dto)) {
                is Outcome.Success -> mapped += investment.value
                is Outcome.Failure -> return investment
            }
        }
        return Outcome.Success(mapped)
    }

    private fun toInvestment(dto: InvestmentPositionDto): Outcome<ApiError, InvestmentPosition> =
        ApiInstant.parseApi(dto.purchasedAt).flatMap { purchasedAt ->
            Money.parseApi(dto.investedAmount).flatMap { invested ->
                QuotePrice.parseApi(dto.purchaseQuote).flatMap { purchaseQuote ->
                    BtcAmount.parseApi(dto.btcAmount).flatMap { btc ->
                        VariationPercent.parseApi(dto.variationPercent).flatMap { variation ->
                            Money.parseApi(dto.currentValue).map { current ->
                                InvestmentPosition(
                                    id = dto.id,
                                    purchasedAt = purchasedAt,
                                    investedAmount = invested,
                                    purchaseQuote = purchaseQuote,
                                    btcAmount = btc,
                                    variationPercent = variation,
                                    currentValue = current,
                                )
                            }
                        }
                    }
                }
            }
        }
}

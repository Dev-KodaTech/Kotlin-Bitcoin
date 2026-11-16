package com.kotlinbitcoins.web.data.mapper

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.error.flatMap
import com.kotlinbitcoins.web.core.error.map
import com.kotlinbitcoins.web.core.model.Liquidation
import com.kotlinbitcoins.web.core.model.RedemptionResult
import com.kotlinbitcoins.web.core.model.Reinvestment
import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.ApiInstant
import com.kotlinbitcoins.web.data.dto.LiquidationDto
import com.kotlinbitcoins.web.data.dto.RedemptionResponseDto
import com.kotlinbitcoins.web.data.dto.ReinvestmentDto

/**
 * [RedemptionResponseDto] → [RedemptionResult], keeping the order of the liquidations (FIFO of the API). Every value
 * is taken as received (Principle II); any value outside the contract makes the whole result [ApiError.Unexpected],
 * which ApiCall.financial turns into an uncertain result: the redemption happened.
 */
object RedemptionMapper {
    fun toRedemptionResult(dto: RedemptionResponseDto): Outcome<ApiError, RedemptionResult> =
        Money.parseApi(dto.amount).flatMap { amount ->
            BtcAmount.parseApi(dto.btcSold).flatMap { btcSold ->
                QuotePrice.parseApi(dto.quote).flatMap { quote ->
                    Money.parseApi(dto.balance).flatMap { balance ->
                        ApiInstant.parseApi(dto.occurredAt).flatMap { occurredAt ->
                            liquidations(dto.liquidations).flatMap { liquidations ->
                                reinvestment(dto.reinvestment).map { reinvestment ->
                                    RedemptionResult(
                                        requestId = dto.requestId,
                                        amount = amount,
                                        btcSold = btcSold,
                                        quote = quote,
                                        balance = balance,
                                        occurredAt = occurredAt,
                                        liquidations = liquidations,
                                        reinvestment = reinvestment,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    private fun liquidations(dtos: List<LiquidationDto>): Outcome<ApiError, List<Liquidation>> {
        val mapped = ArrayList<Liquidation>(dtos.size)
        for (dto in dtos) {
            when (val liquidation = toLiquidation(dto)) {
                is Outcome.Success -> mapped += liquidation.value
                is Outcome.Failure -> return liquidation
            }
        }
        return Outcome.Success(mapped)
    }

    private fun toLiquidation(dto: LiquidationDto): Outcome<ApiError, Liquidation> =
        Money.parseApi(dto.amount).flatMap { amount ->
            BtcAmount.parseApi(dto.btcAmount).map { btc ->
                Liquidation(
                    investmentId = dto.investmentId,
                    amount = amount,
                    btcAmount = btc,
                    isPartial = dto.isPartial,
                )
            }
        }

    private fun reinvestment(dto: ReinvestmentDto?): Outcome<ApiError, Reinvestment?> {
        if (dto == null) return Outcome.Success(null)
        return BtcAmount.parseApi(dto.btcAmount).flatMap { btc ->
            Money.parseApi(dto.investedAmount).flatMap { invested ->
                QuotePrice.parseApi(dto.purchaseQuote).flatMap { purchaseQuote ->
                    ApiInstant.parseApi(dto.purchasedAt).map { purchasedAt ->
                        Reinvestment(
                            investmentId = dto.investmentId,
                            btcAmount = btc,
                            investedAmount = invested,
                            purchaseQuote = purchaseQuote,
                            purchasedAt = purchasedAt,
                        )
                    }
                }
            }
        }
    }
}

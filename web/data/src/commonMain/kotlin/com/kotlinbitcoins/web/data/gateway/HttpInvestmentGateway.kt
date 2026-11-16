package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.error.flatMap
import com.kotlinbitcoins.web.core.error.map
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.core.model.PurchaseResult
import com.kotlinbitcoins.web.core.model.RedemptionResult
import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.ApiInstant
import com.kotlinbitcoins.web.data.dto.AmountRequestDto
import com.kotlinbitcoins.web.data.dto.PositionDto
import com.kotlinbitcoins.web.data.dto.PurchaseResponseDto
import com.kotlinbitcoins.web.data.dto.RedemptionResponseDto
import com.kotlinbitcoins.web.data.http.ApiCall
import com.kotlinbitcoins.web.data.mapper.PositionMapper
import com.kotlinbitcoins.web.data.mapper.RedemptionMapper
import com.kotlinbitcoins.web.state.port.InvestmentGateway
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * [InvestmentGateway] over `GET /me/investments`, `POST /me/purchases` and `POST /me/redemptions`. Values are shown as
 * received.
 */
class HttpInvestmentGateway(
    private val apiCall: ApiCall,
) : InvestmentGateway {
    override suspend fun position(): Outcome<ApiError, Position> =
        apiCall.read(PositionDto.serializer(), PositionMapper::toPosition) {
            method = HttpMethod.Get
            url("me/investments")
        }

    override suspend fun purchase(amount: Money): Outcome<OperationError, PurchaseResult> =
        apiCall.financial(PurchaseResponseDto.serializer(), ::toPurchaseResult) {
            method = HttpMethod.Post
            url("me/purchases")
            contentType(ContentType.Application.Json)
            setBody(AmountRequestDto(amount.toApiString()))
        }

    override suspend fun redeem(amount: Money): Outcome<OperationError, RedemptionResult> =
        apiCall.financial(RedemptionResponseDto.serializer(), RedemptionMapper::toRedemptionResult) {
            method = HttpMethod.Post
            url("me/redemptions")
            contentType(ContentType.Application.Json)
            setBody(AmountRequestDto(amount.toApiString()))
        }

    /** A value outside the contract makes the 201 uncertain in ApiCall.financial: the purchase happened. */
    private fun toPurchaseResult(dto: PurchaseResponseDto): Outcome<ApiError, PurchaseResult> =
        Money.parseApi(dto.amount).flatMap { amount ->
            BtcAmount.parseApi(dto.btcAmount).flatMap { btcAmount ->
                QuotePrice.parseApi(dto.quote).flatMap { quote ->
                    ApiInstant.parseApi(dto.purchasedAt).flatMap { purchasedAt ->
                        Money.parseApi(dto.balance).map { balance ->
                            PurchaseResult(
                                investmentId = dto.investmentId,
                                transactionId = dto.transactionId,
                                amount = amount,
                                btcAmount = btcAmount,
                                quote = quote,
                                purchasedAt = purchasedAt,
                                balance = balance,
                            )
                        }
                    }
                }
            }
        }
}

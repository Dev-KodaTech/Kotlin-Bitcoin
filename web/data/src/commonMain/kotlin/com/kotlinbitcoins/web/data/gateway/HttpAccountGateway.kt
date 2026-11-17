package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.error.flatMap
import com.kotlinbitcoins.web.core.error.map
import com.kotlinbitcoins.web.core.model.DepositResult
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.time.ApiInstant
import com.kotlinbitcoins.web.data.dto.AmountRequestDto
import com.kotlinbitcoins.web.data.dto.BalanceDto
import com.kotlinbitcoins.web.data.dto.DepositResponseDto
import com.kotlinbitcoins.web.data.http.ApiCall
import com.kotlinbitcoins.web.state.port.AccountGateway
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/** [AccountGateway] over the API. Balance and deposit are shown exactly as received (Principle II). */
class HttpAccountGateway(
    private val apiCall: ApiCall,
) : AccountGateway {
    override suspend fun balance(): Outcome<ApiError, Money> =
        apiCall.read(BalanceDto.serializer(), { Money.parseApi(it.balance) }) {
            method = HttpMethod.Get
            url("me/balance")
        }

    override suspend fun deposit(amount: Money): Outcome<OperationError, DepositResult> =
        apiCall.financial(DepositResponseDto.serializer(), ::toDepositResult) {
            method = HttpMethod.Post
            url("me/deposits")
            contentType(ContentType.Application.Json)
            setBody(AmountRequestDto(amount.toApiString()))
        }

    /** A value outside the contract makes the 201 uncertain in ApiCall.financial: the deposit happened. */
    private fun toDepositResult(dto: DepositResponseDto): Outcome<ApiError, DepositResult> =
        Money.parseApi(dto.amount).flatMap { amount ->
            Money.parseApi(dto.balance).flatMap { balance ->
                ApiInstant.parseApi(dto.occurredAt).map { occurredAt ->
                    DepositResult(dto.transactionId, amount, balance, occurredAt)
                }
            }
        }
}

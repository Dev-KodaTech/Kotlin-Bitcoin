package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.data.dto.BalanceDto
import com.kotlinbitcoins.web.data.http.ApiCall
import com.kotlinbitcoins.web.state.port.AccountGateway
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/** [AccountGateway] over the API. The balance is shown exactly as received (Principle II). */
class HttpAccountGateway(
    private val apiCall: ApiCall,
) : AccountGateway {
    override suspend fun balance(): Outcome<ApiError, Money> =
        apiCall.read(BalanceDto.serializer(), { Money.parseApi(it.balance) }) {
            method = HttpMethod.Get
            url("me/balance")
        }
}

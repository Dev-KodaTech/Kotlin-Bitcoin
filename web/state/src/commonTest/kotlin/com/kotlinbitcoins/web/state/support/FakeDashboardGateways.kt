package com.kotlinbitcoins.web.state.support

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.DepositResult
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.core.model.Profile
import com.kotlinbitcoins.web.core.model.PurchaseResult
import com.kotlinbitcoins.web.core.model.Quote
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.state.port.AccountGateway
import com.kotlinbitcoins.web.state.port.InvestmentGateway
import com.kotlinbitcoins.web.state.port.ProfileGateway
import com.kotlinbitcoins.web.state.port.QuoteGateway

/**
 * The four read gateways of the dashboard, the deposit of [AccountGateway] and the purchase of [InvestmentGateway],
 * answering with mutable results and recording the calls.
 */
class FakeDashboardGateways :
    ProfileGateway,
    AccountGateway,
    QuoteGateway,
    InvestmentGateway {
    var profileResult: Outcome<ApiError, Profile> = Outcome.Success(Profile("Maria Souza", "maria@example.com"))
    var balanceResult: Outcome<ApiError, Money> = Outcome.Success(money("1234.56"))
    var quoteResult: Outcome<ApiError, Quote> = Outcome.Success(quote())
    var positionResult: Outcome<ApiError, Position> = Outcome.Success(position(total = "704.00", investments = 3))
    var depositResult: Outcome<OperationError, DepositResult> = Outcome.Success(depositResult())
    var purchaseResult: Outcome<OperationError, PurchaseResult> = Outcome.Success(purchaseResult())

    /** Amounts received by [deposit], in order. */
    val deposits = mutableListOf<Money>()

    /** Amounts received by [purchase], in order. */
    val purchases = mutableListOf<Money>()

    var profileCalls = 0
        private set
    var balanceCalls = 0
        private set
    var quoteCalls = 0
        private set
    var positionCalls = 0
        private set

    override suspend fun profile(): Outcome<ApiError, Profile> = profileResult.also { profileCalls++ }

    override suspend fun balance(): Outcome<ApiError, Money> = balanceResult.also { balanceCalls++ }

    override suspend fun deposit(amount: Money): Outcome<OperationError, DepositResult> =
        depositResult.also { deposits += amount }

    override suspend fun current(): Outcome<ApiError, Quote> = quoteResult.also { quoteCalls++ }

    override suspend fun position(): Outcome<ApiError, Position> = positionResult.also { positionCalls++ }

    override suspend fun purchase(amount: Money): Outcome<OperationError, PurchaseResult> =
        purchaseResult.also { purchases += amount }
}

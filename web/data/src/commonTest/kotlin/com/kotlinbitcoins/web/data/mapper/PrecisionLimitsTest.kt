package com.kotlinbitcoins.web.data.mapper

import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.data.gateway.HttpAccountGateway
import com.kotlinbitcoins.web.data.gateway.HttpInvestmentGateway
import com.kotlinbitcoins.web.data.support.fixtureApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * SC-002: the limits of the contract (R$ 99999999999.99, 0.00000001 BTC and a quote with 8 decimals, fixture
 * `success/limits-200.json`) go from the response to the model and to the screen text with exactly the same digits,
 * without rounding or scientific notation.
 */
class PrecisionLimitsTest {
    @Test
    fun theLargestBalanceKeepsEveryDigit() =
        runTest {
            val api = fixtureApi("limits@GET /me/balance")

            val balance = assertIs<Outcome.Success<Money>>(HttpAccountGateway(api.apiCall).balance()).value

            assertEquals("99999999999.99", balance.toApiString())
            assertEquals("${BRL}99.999.999.999,99", balance.format())
        }

    @Test
    fun oneSatoshiAndQuotesWithEightDecimalsKeepEveryDigit() =
        runTest {
            val api = fixtureApi("limits@GET /me/investments")

            val position = assertIs<Outcome.Success<Position>>(HttpInvestmentGateway(api.apiCall).position()).value

            assertEquals("400000.12345678", position.quote.buyPrice.toPlainString())
            assertEquals("${BRL}400.000,12345678", position.quote.buyPrice.format())
            assertEquals("401000.00000001", position.quote.sellPrice.toPlainString())
            assertEquals("${BRL}401.000,00000001", position.quote.sellPrice.format())
            assertEquals("${BRL}99.999.999.999,99", position.totalCurrentValue.format())

            val (smallest, largest) = position.investments
            assertEquals("0.00000001", smallest.btcAmount.toApiString())
            assertEquals("0,00000001 BTC", smallest.btcAmount.format())
            assertEquals("${BRL}0,01", smallest.investedAmount.format())
            assertEquals("${BRL}400.000,12345678", smallest.purchaseQuote.format())
            assertEquals("${BRL}0,00", smallest.currentValue.format())
            assertEquals("249.376,55860349 BTC", largest.btcAmount.format())
            assertEquals("${BRL}99.999.999.999,99", largest.investedAmount.format())
            assertEquals("${BRL}401.000,00000001", largest.purchaseQuote.format())
        }

    private companion object {
        /** "R$" followed by a no-break space, as the app writes it. */
        const val BRL = "R$ "
    }
}

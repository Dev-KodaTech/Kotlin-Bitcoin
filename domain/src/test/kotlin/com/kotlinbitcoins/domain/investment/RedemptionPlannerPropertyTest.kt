package com.kotlinbitcoins.domain.investment

import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import com.kotlinbitcoins.domain.shared.QuotePrice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import kotlin.random.Random

/**
 * Conservation properties of the redemption plan over random inputs (fixed seed, so a failure is reproducible).
 * The checks are exact: everything is compared as [BigDecimal] or integers, never as floating point.
 */
class RedemptionPlannerPropertyTest {
    private val accountId = AccountId.random()
    private val purchasedAt = Instant.parse("2026-11-01T10:00:00Z")
    private val satsPerBtc = BigDecimal(BtcAmount.SATS_PER_BTC)
    private val cent = BigDecimal("0.01")

    @Test
    fun `every plan conserves credits and satoshis and never over-credits`() {
        val random = Random(SEED)
        var partials = 0
        repeat(CASES) {
            val buyPrice = price(random)
            val investments = investments(random)
            val total =
                investments.fold(
                    Money.ZERO,
                ) { sum, i -> sum + BtcConversion.currentValue(i.btcAmount, buyPrice) }
            val request = request(random, total)
            if (request == null) return@repeat

            val plan = (RedemptionPlanner.plan(request, investments, buyPrice) as Outcome.Success).value
            if (plan.residual != null) partials++

            assertConservation(plan, request, buyPrice)
        }
        assertThat(partials).describedAs("the generator must reach partial liquidations").isGreaterThan(CASES / 10)
    }

    private fun assertConservation(
        plan: RedemptionPlan,
        request: PositiveMoney,
        buyPrice: QuotePrice,
    ) {
        // The credits add up to exactly what was asked.
        val credited = plan.liquidations.fold(Money.ZERO) { sum, l -> sum + l.credit }
        assertThat(credited).isEqualTo(request.money)

        // At most one reinvestment, never of zero satoshis, and only from the last, partially liquidated investment.
        val residual = plan.residual
        val partial = plan.liquidations.filter { it.isPartial }
        assertThat(partial.size).isLessThanOrEqualTo(1)
        assertThat(residual != null).isEqualTo(partial.size == 1)

        plan.liquidations.forEach { liquidation ->
            val held = liquidation.investment.btcAmount.sats
            val soldSats = liquidation.soldSats.sats
            val residualSats =
                if (residual != null &&
                    residual.origin == liquidation.investment
                ) {
                    residual.sats.sats
                } else {
                    0L
                }
            assertThat(soldSats + residualSats).describedAs("sold + residual = original").isEqualTo(held)
            if (residual != null && residual.origin == liquidation.investment) assertThat(residualSats).isPositive()

            val exact = BigDecimal(soldSats).multiply(buyPrice.value).divide(satsPerBtc)
            assertThat(liquidation.credit.amount)
                .describedAs("never credits more than the bitcoin is worth")
                .isLessThanOrEqualTo(exact)
            if (liquidation.isPartial) {
                // CEILING in the satoshi: the satoshis sold exceed the ones the credit needs by less than one.
                val needed =
                    liquidation.credit.amount
                        .multiply(
                            satsPerBtc,
                        ).divide(buyPrice.value, 20, RoundingMode.HALF_UP)
                assertThat(BigDecimal(soldSats).subtract(needed)).isLessThan(BigDecimal.ONE)
            } else {
                // FLOOR in the cent: a whole liquidation credits less than one cent below the exact value.
                assertThat(exact.subtract(liquidation.credit.amount)).isLessThan(cent)
            }
        }
        assertThat(plan.totalSoldSats.sats).isEqualTo(plan.liquidations.sumOf { it.soldSats.sats })
    }

    private fun price(random: Random): QuotePrice =
        (QuotePrice.of(BigDecimal.valueOf(random.nextLong(1_000, 900_000_000_00L), 2)) as Outcome.Success).value

    private fun investments(random: Random): List<Investment> =
        List(random.nextInt(1, 6)) { index ->
            val sats = random.nextLong(1, 500_000_000L)
            Investment.purchase(accountId, purchasedAt, index.toLong(), Money.ZERO, price(random), BtcAmount(sats))
        }

    /** A positive request no larger than [total], or null when the total is zero (nothing can be asked). */
    private fun request(
        random: Random,
        total: Money,
    ): PositiveMoney? {
        val cents = total.amount.movePointRight(2).longValueExact()
        if (cents < 1) return null
        val asked = if (random.nextInt(4) == 0) cents else random.nextLong(1, cents + 1)
        return (PositiveMoney.of(BigDecimal.valueOf(asked, 2)) as Outcome.Success).value
    }

    private companion object {
        const val SEED = 20_261_110
        const val CASES = 10_000
    }
}

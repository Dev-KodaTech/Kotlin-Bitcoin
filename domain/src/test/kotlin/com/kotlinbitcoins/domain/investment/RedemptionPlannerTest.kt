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
import java.time.Instant

class RedemptionPlannerTest {
    private val accountId = AccountId.random()
    private val day1 = Instant.parse("2026-11-01T10:00:00Z")
    private val day2 = Instant.parse("2026-11-02T10:00:00Z")

    @Test
    fun `scenario 1 - a request equal to the oldest investment liquidates it whole and leaves the next intact`() {
        // A is worth 300.00 and B 500.00 at 400000.00
        val a = investment(day1, 1, "400000.00", 75_000)
        val b = investment(day2, 2, "400000.00", 125_000)

        val plan = plan("300.00", "400000.00", a, b)

        assertThat(plan.liquidations).hasSize(1)
        val liquidation = plan.liquidations.single()
        assertThat(liquidation.investment).isEqualTo(a)
        assertThat(liquidation.soldSats).isEqualTo(BtcAmount(75_000))
        assertThat(liquidation.credit).isEqualTo(money("300.00"))
        assertThat(liquidation.isPartial).isFalse()
        assertThat(plan.residual).isNull()
    }

    @Test
    fun `scenario 2 - a request that eats A and part of B liquidates B partially and keeps a residual worth 300`() {
        val a = investment(day1, 1, "400000.00", 75_000)
        val b = investment(day2, 2, "400000.00", 125_000)

        val plan = plan("500.00", "400000.00", a, b)

        assertThat(plan.liquidations.map { it.investment }).containsExactly(a, b)
        assertThat(plan.liquidations.map { it.credit }).containsExactly(money("300.00"), money("200.00"))
        assertThat(plan.liquidations.map { it.isPartial }).containsExactly(false, true)
        assertThat(plan.liquidations[1].soldSats).isEqualTo(BtcAmount(50_000))
        assertThat(plan.residual?.origin).isEqualTo(b)
        assertThat(plan.residual?.sats).isEqualTo(BtcAmount(75_000))
        assertThat(plan.totalSoldSats).isEqualTo(BtcAmount(125_000))
    }

    @Test
    fun `scenario 3 - 1 BTC bought at 100000 with the buy price at 200000 sells a quarter to cover 50000`() {
        val one = investment(day1, 1, "100000.00", 100_000_000)

        val plan = plan("50000.00", "200000.00", one)

        val liquidation = plan.liquidations.single()
        assertThat(liquidation.soldSats).isEqualTo(BtcAmount(25_000_000))
        assertThat(liquidation.credit).isEqualTo(money("50000.00"))
        assertThat(liquidation.isPartial).isTrue()
        assertThat(plan.residual?.sats).isEqualTo(BtcAmount(75_000_000))
        assertThat(plan.residual?.origin).isEqualTo(one)
    }

    @Test
    fun `a request equal to the total liquidates everything with no reinvestment`() {
        val a = investment(day1, 1, "400000.00", 75_000)
        val b = investment(day2, 2, "400000.00", 125_000)

        val plan = plan("800.00", "400000.00", a, b)

        assertThat(plan.liquidations.map { it.isPartial }).containsExactly(false, false)
        assertThat(plan.liquidations.map { it.credit }).containsExactly(money("300.00"), money("500.00"))
        assertThat(plan.residual).isNull()
        assertThat(plan.totalSoldSats).isEqualTo(BtcAmount(200_000))
    }

    @Test
    fun `a request that ends exactly at the end of an investment liquidates it whole without reinvestment`() {
        val a = investment(day1, 1, "400000.00", 75_000)
        val b = investment(day2, 2, "400000.00", 125_000)

        val plan = plan("300.00", "400000.00", a, b)

        assertThat(plan.liquidations.map { it.investment }).containsExactly(a)
        assertThat(plan.residual).isNull()
    }

    @Test
    fun `when rounding up the satoshis covers the whole investment it is liquidated whole and credits the request`() {
        // 1 sat at 100000000.00 per BTC is worth 1.00; asking for 0.99 needs CEILING(0.99) = 1 sat = everything
        val tiny = investment(day1, 1, "100000000.00", 1)
        val bigger = investment(day2, 2, "100000000.00", 100)

        val plan = plan("0.99", "100000000.00", tiny, bigger)

        val liquidation = plan.liquidations.single()
        assertThat(liquidation.investment).isEqualTo(tiny)
        assertThat(liquidation.soldSats).isEqualTo(BtcAmount(1))
        assertThat(liquidation.credit).isEqualTo(money("0.99"))
        assertThat(liquidation.isPartial).isFalse()
        assertThat(plan.residual).isNull()
    }

    @Test
    fun `a request above the total is RedemptionExceedsAvailable`() {
        val a = investment(day1, 1, "400000.00", 75_000)
        val b = investment(day2, 2, "400000.00", 125_000)

        val result = RedemptionPlanner.plan(positive("800.01"), listOf(a, b), price("400000.00"))

        assertThat(result).isEqualTo(Outcome.Failure(RedemptionExceedsAvailable))
    }

    @Test
    fun `without investments any request is RedemptionExceedsAvailable`() {
        val result = RedemptionPlanner.plan(positive("0.01"), emptyList(), price("400000.00"))

        assertThat(result).isEqualTo(Outcome.Failure(RedemptionExceedsAvailable))
    }

    @Test
    fun `a residue of a few satoshis worth zero is liquidated first and credits zero`() {
        // 1 sat at 400000.00 is worth 0.004, i.e. 0.00 after FLOOR
        val dust = investment(day1, 1, "400000.00", 1)
        val b = investment(day2, 2, "400000.00", 125_000)

        val plan = plan("100.00", "400000.00", dust, b)

        assertThat(plan.liquidations.map { it.investment }).containsExactly(dust, b)
        assertThat(plan.liquidations[0].credit).isEqualTo(Money.ZERO)
        assertThat(plan.liquidations[0].soldSats).isEqualTo(BtcAmount(1))
        assertThat(plan.liquidations[0].isPartial).isFalse()
        assertThat(plan.liquidations[1].credit).isEqualTo(money("100.00"))
        assertThat(plan.liquidations[1].isPartial).isTrue()
    }

    @Test
    fun `the plan follows the order of the list it receives, which is the FIFO order`() {
        val first = investment(day1, 1, "400000.00", 50_000)
        val second = investment(day1, 2, "400000.00", 50_000)

        val plan = plan("250.00", "400000.00", first, second)

        assertThat(plan.liquidations.map { it.investment }).containsExactly(first, second)
        assertThat(plan.liquidations.map { it.credit }).containsExactly(money("200.00"), money("50.00"))
    }

    private fun plan(
        amount: String,
        buyPrice: String,
        vararg investments: Investment,
    ): RedemptionPlan =
        (RedemptionPlanner.plan(positive(amount), investments.toList(), price(buyPrice)) as Outcome.Success).value

    private fun investment(
        purchasedAt: Instant,
        fifoSequence: Long,
        purchaseQuote: String,
        sats: Long,
    ) = Investment.purchase(
        accountId = accountId,
        purchasedAt = purchasedAt,
        fifoSequence = fifoSequence,
        investedAmount = Money.ZERO,
        purchaseQuote = price(purchaseQuote),
        btcAmount = BtcAmount(sats),
    )

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    private fun positive(value: String): PositiveMoney = (PositiveMoney.of(BigDecimal(value)) as Outcome.Success).value

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}

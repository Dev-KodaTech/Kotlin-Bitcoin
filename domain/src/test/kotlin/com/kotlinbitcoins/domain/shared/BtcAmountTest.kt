package com.kotlinbitcoins.domain.shared

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BtcAmountTest {
    @Test
    fun `accepts zero and positive satoshis`() {
        assertThat(BtcAmount(0).sats).isZero()
        assertThat(BtcAmount(1).sats).isEqualTo(1)
    }

    @Test
    fun `rejects negative satoshis`() {
        assertThatThrownBy { BtcAmount(-1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `plus and minus work on satoshis`() {
        assertThat(BtcAmount(150) + BtcAmount(50)).isEqualTo(BtcAmount(200))
        assertThat(BtcAmount(150) - BtcAmount(50)).isEqualTo(BtcAmount(100))
    }

    @Test
    fun `minus that would go negative violates the invariant`() {
        assertThatThrownBy { BtcAmount(1) - BtcAmount(2) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `ZERO has no satoshis`() {
        assertThat(BtcAmount.ZERO.sats).isZero()
    }

    @Test
    fun `toBtcDecimal uses scale 8`() {
        assertThat(BtcAmount(100_000).toBtcDecimal()).isEqualTo(BigDecimal("0.00100000"))
        assertThat(BtcAmount(BtcAmount.SATS_PER_BTC).toBtcDecimal()).isEqualTo(BigDecimal("1.00000000"))
    }
}

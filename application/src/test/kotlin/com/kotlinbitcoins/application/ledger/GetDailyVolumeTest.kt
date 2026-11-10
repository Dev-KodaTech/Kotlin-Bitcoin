package com.kotlinbitcoins.application.ledger

import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.domain.ledger.TransactionType
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Outcome
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GetDailyVolumeTest {
    // 22:00 of 2026-11-09 in São Paulo, already 2026-11-10 in UTC.
    private val now = Instant.parse("2026-11-10T01:00:00Z")
    private val startOfLocalDay = Instant.parse("2026-11-09T03:00:00Z")
    private val startOfNextLocalDay = Instant.parse("2026-11-10T03:00:00Z")
    private val transactions = mockk<TransactionRepository>()
    private val clock = Clock { now }

    private val getDailyVolume = GetDailyVolume(transactions, clock, ZoneId.of("America/Sao_Paulo"))

    @Test
    fun `sums purchases and redemptions of the current local day and reports its date`() {
        every {
            transactions.sumBtcByTypeBetween(TransactionType.PURCHASE, startOfLocalDay, startOfNextLocalDay)
        } returns Outcome.Success(BtcAmount(300_000))
        every {
            transactions.sumBtcByTypeBetween(TransactionType.REDEMPTION, startOfLocalDay, startOfNextLocalDay)
        } returns Outcome.Success(BtcAmount(50_000))

        val result = getDailyVolume.execute()

        val volume = (result as Outcome.Success).value
        assertThat(volume.date).isEqualTo(LocalDate.parse("2026-11-09"))
        assertThat(volume.boughtBtc).isEqualTo(BtcAmount(300_000))
        assertThat(volume.soldBtc).isEqualTo(BtcAmount(50_000))
    }

    @Test
    fun `reinvestments and deposits are never queried`() {
        every { transactions.sumBtcByTypeBetween(any(), any(), any()) } returns Outcome.Success(BtcAmount.ZERO)

        getDailyVolume.execute()

        verify(exactly = 0) { transactions.sumBtcByTypeBetween(TransactionType.REINVESTMENT, any(), any()) }
        verify(exactly = 0) { transactions.sumBtcByTypeBetween(TransactionType.DEPOSIT, any(), any()) }
    }

    @Test
    fun `without operations both totals are zero`() {
        every { transactions.sumBtcByTypeBetween(any(), any(), any()) } returns Outcome.Success(BtcAmount.ZERO)

        val volume = (getDailyVolume.execute() as Outcome.Success).value

        assertThat(volume.boughtBtc).isEqualTo(BtcAmount.ZERO)
        assertThat(volume.soldBtc).isEqualTo(BtcAmount.ZERO)
    }

    @Test
    fun `failure of the repository is ServiceUnavailable and writes nothing`() {
        every { transactions.sumBtcByTypeBetween(any(), any(), any()) } returns
            Outcome.Failure(InfrastructureFailure("db down"))

        val result = getDailyVolume.execute()

        assertThat(result).isEqualTo(Outcome.Failure(DailyVolumeError.ServiceUnavailable))
        verify(exactly = 0) { transactions.saveAll(any()) }
    }
}

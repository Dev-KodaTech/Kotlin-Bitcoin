package com.kotlinbitcoins.application.ledger

import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.InfrastructureUnavailable
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.domain.ledger.TransactionType
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.flatMap
import com.kotlinbitcoins.domain.shared.map
import com.kotlinbitcoins.domain.shared.mapError
import java.time.LocalDate
import java.time.ZoneId

/** Read model of US9: market totals of one local day. It carries no client data (FR-026). */
data class DailyVolume(
    val date: LocalDate,
    val boughtBtc: BtcAmount,
    val soldBtc: BtcAmount,
)

sealed interface DailyVolumeError {
    data object ServiceUnavailable : DailyVolumeError, InfrastructureUnavailable
}

/**
 * Total bitcoin bought and sold today by every client (US9, FR-026). "Today" is the current day in the business zone
 * (FR-034a), so the day runs from 00:00:00 local up to, and excluding, the next 00:00:00 local. Only purchases and
 * redemptions count: a reinvestment is the residual of a redemption and is not a purchase.
 * A read has no side effect, so it needs neither a transaction nor a lock.
 */
class GetDailyVolume(
    private val transactions: TransactionRepository,
    private val clock: Clock,
    private val businessZone: ZoneId,
) {
    fun execute(): Outcome<DailyVolumeError, DailyVolume> {
        val today = clock.now().atZone(businessZone).toLocalDate()
        val from = today.atStartOfDay(businessZone).toInstant()
        val to = today.plusDays(1).atStartOfDay(businessZone).toInstant()

        return transactions
            .sumBtcByTypeBetween(TransactionType.PURCHASE, from, to)
            .flatMap { bought ->
                transactions
                    .sumBtcByTypeBetween(TransactionType.REDEMPTION, from, to)
                    .map { sold -> DailyVolume(today, bought, sold) }
            }.mapError<_, _, DailyVolumeError> { DailyVolumeError.ServiceUnavailable }
    }
}

package com.kotlinbitcoins.api.market

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.application.ledger.DailyVolume
import com.kotlinbitcoins.application.ledger.GetDailyVolume
import com.kotlinbitcoins.domain.shared.fold
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** `date` is the local day (America/Sao_Paulo) of the totals; there is nothing here that identifies a client. */
data class DailyVolumeResponse(
    val date: String,
    val boughtBtc: String,
    val soldBtc: String,
) {
    companion object {
        fun of(
            volume: DailyVolume,
            formats: ApiFormats,
        ) = DailyVolumeResponse(
            date = volume.date.toString(),
            boughtBtc = formats.btc(volume.boughtBtc),
            soldBtc = formats.btc(volume.soldBtc),
        )
    }
}

/** Aggregated market data, open to any authenticated client (US9, FR-026). */
@RestController
class MarketController(
    private val getDailyVolume: GetDailyVolume,
    private val formats: ApiFormats,
    private val problems: ProblemDetailsMapper,
) {
    @GetMapping("/api/v1/market/daily-volume")
    fun dailyVolume(): ResponseEntity<*> =
        getDailyVolume
            .execute()
            .fold(
                onFailure = problems::dailyVolumeError,
                onSuccess = { ResponseEntity.ok(DailyVolumeResponse.of(it, formats)) },
            )
}

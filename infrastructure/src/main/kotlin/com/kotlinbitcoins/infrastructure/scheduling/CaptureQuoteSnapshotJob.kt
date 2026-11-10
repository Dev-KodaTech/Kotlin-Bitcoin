package com.kotlinbitcoins.infrastructure.scheduling

import com.kotlinbitcoins.application.quote.CaptureQuoteSnapshot
import com.kotlinbitcoins.application.quote.QuoteHistoryError
import com.kotlinbitcoins.domain.shared.Outcome
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/**
 * Captures the quote at every 10 minute mark of the business zone (FR-027). ShedLock lets only one replica run each
 * tick, so the source is called once per slot. The class and [run] are `open` because ShedLock wraps the bean in a
 * proxy. A failure is logged and never thrown, so the schedule goes on (R-03).
 */
open class CaptureQuoteSnapshotJob(
    private val captureQuoteSnapshot: CaptureQuoteSnapshot,
) {
    @Scheduled(cron = "0 */10 * * * *", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "captureQuoteSnapshot", lockAtMostFor = "PT5M")
    open fun run() {
        when (val result = captureQuoteSnapshot.execute()) {
            is Outcome.Success -> {
                logger.info("Quote snapshot captured for slot {}", result.value.slotAt)
            }

            is Outcome.Failure -> {
                when (result.error) {
                    QuoteHistoryError.SourceUnavailable -> logger.warn("Quote snapshot skipped: source unavailable")
                    QuoteHistoryError.ServiceUnavailable -> logger.warn("Quote snapshot failed: storage unavailable")
                }
            }
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(CaptureQuoteSnapshotJob::class.java)
    }
}

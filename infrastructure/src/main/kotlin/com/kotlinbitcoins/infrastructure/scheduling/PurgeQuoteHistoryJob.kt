package com.kotlinbitcoins.infrastructure.scheduling

import com.kotlinbitcoins.application.quote.PurgeQuoteHistory
import com.kotlinbitcoins.domain.shared.Outcome
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/**
 * Removes the quote history with more than 90 days, every day at 03:00 in the business zone (FR-029). ShedLock keeps
 * it to one replica. The class and [run] are `open` because ShedLock wraps the bean in a proxy.
 */
open class PurgeQuoteHistoryJob(
    private val purgeQuoteHistory: PurgeQuoteHistory,
) {
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "purgeQuoteHistory")
    open fun run() {
        when (val result = purgeQuoteHistory.execute()) {
            is Outcome.Success -> logger.info("Quote history purged: {} snapshot(s) removed", result.value)
            is Outcome.Failure -> logger.warn("Quote history purge failed: storage unavailable")
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(PurgeQuoteHistoryJob::class.java)
    }
}

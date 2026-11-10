package com.kotlinbitcoins.infrastructure.scheduling

import com.kotlinbitcoins.application.quote.CaptureQuoteSnapshot
import com.kotlinbitcoins.application.quote.PurgeQuoteHistory
import com.kotlinbitcoins.application.quote.QuoteHistoryError
import com.kotlinbitcoins.domain.quote.QuoteSnapshot
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import com.kotlinbitcoins.infrastructure.support.PostgresIntegrationTest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@Import(QuoteHistoryJobsIT.JobsConfiguration::class)
class QuoteHistoryJobsIT : PostgresIntegrationTest() {
    @Autowired
    private lateinit var captureJob: CaptureQuoteSnapshotJob

    @Autowired
    private lateinit var purgeJob: PurgeQuoteHistoryJob

    @Autowired
    private lateinit var capture: CaptureQuoteSnapshot

    @Autowired
    private lateinit var purge: PurgeQuoteHistory

    @BeforeEach
    fun resetMocks() {
        clearMocks(capture, purge)
    }

    @Test
    fun `the capture job delegates to the use case`() {
        every { capture.execute() } returns Outcome.Success(snapshot())

        captureJob.run()

        verify(exactly = 1) { capture.execute() }
    }

    @Test
    fun `the purge job delegates to the use case`() {
        every { purge.execute() } returns Outcome.Success(2)

        purgeJob.run()

        verify(exactly = 1) { purge.execute() }
    }

    @Test
    fun `a source failure is logged and does not throw`() {
        every { capture.execute() } returns Outcome.Failure(QuoteHistoryError.SourceUnavailable)

        captureJob.run()

        verify(exactly = 1) { capture.execute() }
    }

    @Test
    fun `an infrastructure failure is logged and does not throw`() {
        every { capture.execute() } returns Outcome.Failure(QuoteHistoryError.ServiceUnavailable)
        every { purge.execute() } returns Outcome.Failure(QuoteHistoryError.ServiceUnavailable)

        captureJob.run()
        purgeJob.run()

        verify(exactly = 1) { capture.execute() }
        verify(exactly = 1) { purge.execute() }
    }

    @Test
    fun `two simultaneous captures with ShedLock run the use case only once`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        every { capture.execute() } answers {
            entered.countDown()
            release.await(RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            Outcome.Success(snapshot())
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit { captureJob.run() }
            assertThat(entered.await(RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()

            // The first run holds the lock; the second one must be skipped, not queued.
            val second = executor.submit { captureJob.run() }
            second.get(RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            release.countDown()
            first.get(RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }

        verify(exactly = 1) { capture.execute() }
    }

    private fun snapshot(): QuoteSnapshot {
        val price = (QuotePrice.of(BigDecimal("400000.00")) as Outcome.Success).value
        val slot = Instant.parse("2026-11-09T11:10:00Z")
        return QuoteSnapshot(slot, price, price, slot)
    }

    @TestConfiguration
    @EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
    class JobsConfiguration {
        @Bean
        fun lockProvider(dataSource: DataSource): LockProvider =
            JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration
                    .builder()
                    .withJdbcTemplate(JdbcTemplate(dataSource))
                    .usingDbTime()
                    .build(),
            )

        @Bean
        fun captureQuoteSnapshot(): CaptureQuoteSnapshot = mockk()

        @Bean
        fun purgeQuoteHistory(): PurgeQuoteHistory = mockk()

        @Bean
        fun captureQuoteSnapshotJob(captureQuoteSnapshot: CaptureQuoteSnapshot): CaptureQuoteSnapshotJob =
            CaptureQuoteSnapshotJob(captureQuoteSnapshot)

        @Bean
        fun purgeQuoteHistoryJob(purgeQuoteHistory: PurgeQuoteHistory): PurgeQuoteHistoryJob =
            PurgeQuoteHistoryJob(purgeQuoteHistory)
    }

    private companion object {
        const val RELEASE_TIMEOUT_SECONDS = 10L
    }
}

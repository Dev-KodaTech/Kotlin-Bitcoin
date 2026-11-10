package com.kotlinbitcoins.bootstrap.performance

import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SC-007: 200 deposits in a row, with the provider taking 50 ms per e-mail, must all be notified, and the 95th
 * percentile of the time from the event being recorded (`created_at`) to the e-mail being sent (`sent_at`) must stay
 * at or under 5 minutes. Both columns come from the `Clock` port, so a thread keeps the test clock on real time.
 */
class EmailThroughputTest : ApplicationTestBase() {
    @Test
    fun `200 deposits are all notified with p95 of the delay at most 5 minutes`() {
        val running = AtomicBoolean(true)
        val ticker =
            Thread {
                while (running.get()) {
                    clock.set(Instant.now())
                    Thread.sleep(TICK_MILLIS)
                }
            }.apply {
                isDaemon = true
                start()
            }
        try {
            emailSender.delay = Duration.ofMillis(SEND_MILLIS)
            val ana = api.register("Ana", "ana@example.com")

            repeat(DEPOSITS) { api.depositOk(ana, "10.00") }

            await().atMost(Duration.ofMinutes(4)).pollInterval(Duration.ofSeconds(1)).untilAsserted {
                assertThat(count("sent_at IS NOT NULL")).describedAs("events sent").isEqualTo(DEPOSITS)
            }
            assertThat(count("TRUE")).isEqualTo(DEPOSITS)
            assertThat(emailSender.messages).hasSize(DEPOSITS)
            val p95Seconds =
                jdbcTemplate.queryForObject(
                    "SELECT percentile_cont(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM sent_at - created_at)) " +
                        "FROM outbox_events",
                    Double::class.java,
                )!!
            assertThat(p95Seconds).describedAs("p95 delay in seconds").isLessThanOrEqualTo(LIMIT.seconds.toDouble())
        } finally {
            running.set(false)
            ticker.join()
        }
    }

    private fun count(condition: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE $condition", Int::class.java)!!

    private companion object {
        const val DEPOSITS = 200
        const val SEND_MILLIS = 50L
        const val TICK_MILLIS = 20L
        val LIMIT: Duration = Duration.ofMinutes(5)
    }
}

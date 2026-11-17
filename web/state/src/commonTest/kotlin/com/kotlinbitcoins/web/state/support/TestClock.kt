package com.kotlinbitcoins.web.state.support

import com.kotlinbitcoins.web.state.port.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Wall clock that follows the virtual time of the test. [skew] moves the wall clock without running timers,
 * like a device that slept and woke up after the expiry instant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestClock(
    private val scheduler: TestCoroutineScheduler,
    private val origin: Instant = Instant.fromEpochMilliseconds(ORIGIN_EPOCH_MS),
) : Clock {
    var skew: Duration = Duration.ZERO

    override fun now(): Instant = origin + scheduler.currentTime.milliseconds + skew

    companion object {
        const val ORIGIN_EPOCH_MS = 1_794_221_400_000L
    }
}

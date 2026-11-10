package com.kotlinbitcoins.infrastructure.time

import com.kotlinbitcoins.application.port.Clock
import java.time.Instant

/** The only [Clock] implementation that reads the real time (UTC). */
class SystemClock(
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
) : Clock {
    override fun now(): Instant = clock.instant()
}

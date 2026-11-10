package com.kotlinbitcoins.bootstrap.support

import com.kotlinbitcoins.application.port.Clock
import java.time.Duration
import java.time.Instant

/** Test [Clock] that tests can set and advance. Replaces `SystemClock` as a `@Primary` bean. */
class MutableClock(
    initial: Instant,
) : Clock {
    @Volatile
    private var current: Instant = initial

    override fun now(): Instant = current

    fun set(instant: Instant) {
        current = instant
    }

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}

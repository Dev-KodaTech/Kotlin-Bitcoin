package com.kotlinbitcoins.web.data.time

import com.kotlinbitcoins.web.state.port.Clock
import kotlin.time.Instant

/** The real wall clock. */
object SystemClock : Clock {
    override fun now(): Instant =
        kotlin.time.Clock.System
            .now()
}

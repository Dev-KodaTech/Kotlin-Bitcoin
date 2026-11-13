package com.kotlinbitcoins.web.state.port

import kotlin.time.Instant

/** Wall clock; the only source of "now" in :state (testable with virtual time). */
fun interface Clock {
    fun now(): Instant
}

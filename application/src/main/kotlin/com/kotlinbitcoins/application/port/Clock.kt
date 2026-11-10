package com.kotlinbitcoins.application.port

import java.time.Instant

/** Source of the current instant. Business code never calls `Instant.now()` directly. */
fun interface Clock {
    fun now(): Instant
}

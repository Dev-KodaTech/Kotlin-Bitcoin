package com.kotlinbitcoins.application.port

/**
 * Infrastructure failure (database, cache, broker) already converted to a value by the adapter.
 * [reason] is a short description for logs; it carries no stack trace and is never shown to the client.
 */
data class InfrastructureFailure(
    val reason: String,
)

/**
 * Marker for the `ServiceUnavailable` variant of each operation error. Subtypes of a sealed interface must live in
 * its own package, so each operation declares its own variant and tags it with this marker; the HTTP layer maps any
 * marked error to `SERVICE_UNAVAILABLE` (503).
 */
interface InfrastructureUnavailable

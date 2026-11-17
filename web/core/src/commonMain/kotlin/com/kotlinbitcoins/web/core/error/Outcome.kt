package com.kotlinbitcoins.web.core.error

/**
 * Result of an operation that can fail with a typed error (Principle III). Exceptions never cross the module
 * boundaries: :data converts them into [Failure] values.
 */
sealed interface Outcome<out E, out T> {
    data class Success<out T>(
        val value: T,
    ) : Outcome<Nothing, T>

    data class Failure<out E>(
        val error: E,
    ) : Outcome<E, Nothing>
}

inline fun <E, T, R> Outcome<E, T>.map(transform: (T) -> R): Outcome<E, R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(value))
        is Outcome.Failure -> this
    }

inline fun <E, T, R> Outcome<E, T>.flatMap(transform: (T) -> Outcome<E, R>): Outcome<E, R> =
    when (this) {
        is Outcome.Success -> transform(value)
        is Outcome.Failure -> this
    }

inline fun <E, F, T> Outcome<E, T>.mapError(transform: (E) -> F): Outcome<F, T> =
    when (this) {
        is Outcome.Success -> this
        is Outcome.Failure -> Outcome.Failure(transform(error))
    }

inline fun <E, T, R> Outcome<E, T>.fold(
    onFailure: (E) -> R,
    onSuccess: (T) -> R,
): R =
    when (this) {
        is Outcome.Success -> onSuccess(value)
        is Outcome.Failure -> onFailure(error)
    }

fun <T> T.asSuccess(): Outcome<Nothing, T> = Outcome.Success(this)

fun <E> E.asFailure(): Outcome<E, Nothing> = Outcome.Failure(this)

/** Turns a nullable value into an [Outcome], failing with [error] when it is null. */
fun <E, T : Any> T?.toOutcome(error: E): Outcome<E, T> = this?.let { Outcome.Success(it) } ?: Outcome.Failure(error)

package com.kotlinbitcoins.domain.shared

/**
 * Result of an operation that can fail in a predictable way (Principle III).
 * Predictable failures travel as [Failure] values with a typed error; exceptions are not used for flow control.
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

inline fun <E, T, F> Outcome<E, T>.mapError(transform: (E) -> F): Outcome<F, T> =
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

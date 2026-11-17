package com.kotlinbitcoins.web.state.shared

import com.kotlinbitcoins.web.core.error.ApiError

/** State of a block that loads data (FR-042): loading, loaded, empty and error are distinct. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>

    data class Loaded<out T>(
        val value: T,
    ) : Loadable<T>

    /** Only where the spec defines an empty state (investments, statement, history). */
    data object Empty : Loadable<Nothing>

    data class Failed(
        val error: ApiError,
    ) : Loadable<Nothing>
}

package com.kotlinbitcoins.web.state.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Periodic refresh (W-10, FR-011/FR-012): runs on start, then [interval] after the end of the previous cycle
 * (no overlap). Hidden or paused, it stops with no timer; becoming visible or resumed runs immediately.
 */
class AutoRefresher(
    private val visible: StateFlow<Boolean>,
    private val interval: Duration = DEFAULT_INTERVAL,
    private val refresh: suspend () -> Unit,
) {
    private val paused = MutableStateFlow(false)

    val isPaused: StateFlow<Boolean> = paused.asStateFlow()

    fun setPaused(value: Boolean) {
        paused.value = value
    }

    fun start(scope: CoroutineScope): Job =
        scope.launch {
            combine(visible, paused) { isVisible, isPaused -> isVisible && !isPaused }
                .distinctUntilChanged()
                .collectLatest { active ->
                    while (active) {
                        refresh()
                        delay(interval)
                    }
                }
        }

    companion object {
        val DEFAULT_INTERVAL: Duration = 10.seconds
    }
}

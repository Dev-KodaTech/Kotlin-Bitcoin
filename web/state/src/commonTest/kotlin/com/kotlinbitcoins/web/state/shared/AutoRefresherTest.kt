package com.kotlinbitcoins.web.state.shared

import com.kotlinbitcoins.web.state.support.FakePageVisibility
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AutoRefresherTest {
    private val visibility = FakePageVisibility()
    private val calls = mutableListOf<Long>()

    private fun TestScope.refresher(duration: Long = 0): AutoRefresher =
        AutoRefresher(visibility.isVisible) {
            calls += currentTime
            delay(duration)
        }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun runsOnStartAndTenSecondsAfterEachCycle() =
        runTest {
            refresher().start(backgroundScope)
            runCurrent()
            assertEquals(listOf(0L), calls)
            advance(9_999)
            assertEquals(listOf(0L), calls)
            advance(1)
            advance(10_000)
            assertEquals(listOf(0L, 10_000L, 20_000L), calls)
        }

    @Test
    fun waitsForTheEndOfASlowCycleWithoutOverlap() =
        runTest {
            var running = 0
            var maxRunning = 0
            AutoRefresher(visibility.isVisible) {
                calls += currentTime
                running++
                maxRunning = maxOf(maxRunning, running)
                delay(15_000)
                running--
            }.start(backgroundScope)
            runCurrent()
            advance(60_000)
            assertEquals(listOf(0L, 25_000L, 50_000L), calls)
            assertEquals(1, maxRunning)
        }

    @Test
    fun doesNotRunWhileHiddenAndRunsImmediatelyWhenVisible() =
        runTest {
            visibility.isVisible.value = false
            refresher().start(backgroundScope)
            advance(60_000)
            assertTrue(calls.isEmpty())

            visibility.isVisible.value = true
            runCurrent()
            assertEquals(listOf(60_000L), calls)
        }

    @Test
    fun hidingStopsTheTimerAndShowingRunsAtOnce() =
        runTest {
            refresher().start(backgroundScope)
            runCurrent()
            advance(5_000)
            visibility.isVisible.value = false
            advance(25_000)
            assertEquals(listOf(0L), calls)

            visibility.isVisible.value = true
            runCurrent()
            assertEquals(listOf(0L, 30_000L), calls)
            advance(10_000)
            assertEquals(listOf(0L, 30_000L, 40_000L), calls)
        }

    @Test
    fun pauseStopsAndResumeRunsImmediately() =
        runTest {
            val refresher = refresher()
            refresher.start(backgroundScope)
            runCurrent()
            refresher.setPaused(true)
            runCurrent()
            assertTrue(refresher.isPaused.value)
            advance(60_000)
            assertEquals(listOf(0L), calls)

            refresher.setPaused(false)
            runCurrent()
            assertEquals(listOf(0L, 60_000L), calls)
        }

    @Test
    fun startingPausedDoesNotRun() =
        runTest {
            val refresher = refresher()
            refresher.setPaused(true)
            refresher.start(backgroundScope)
            advance(30_000)
            assertTrue(calls.isEmpty())
        }
}

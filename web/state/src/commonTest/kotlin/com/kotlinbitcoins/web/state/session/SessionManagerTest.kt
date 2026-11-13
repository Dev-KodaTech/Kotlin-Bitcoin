package com.kotlinbitcoins.web.state.session

import com.kotlinbitcoins.web.state.port.StoredSession
import com.kotlinbitcoins.web.state.support.FakeSessionStorage
import com.kotlinbitcoins.web.state.support.FakeTabChannel
import com.kotlinbitcoins.web.state.support.FakeTabNetwork
import com.kotlinbitcoins.web.state.support.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private val network = FakeTabNetwork()

    private class Tab(
        val manager: SessionManager,
        val storage: FakeSessionStorage,
        val channel: FakeTabChannel,
        val clock: TestClock,
        val events: MutableList<SessionEvent>,
    )

    private fun TestScope.tab(stored: StoredSession? = null): Tab {
        val storage = FakeSessionStorage(stored)
        val channel = network.newTab()
        val clock = TestClock(testScheduler)
        val manager = SessionManager(storage, channel, clock, backgroundScope)
        val events = mutableListOf<SessionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { manager.events.collect(events::add) }
        return Tab(manager, storage, channel, clock, events)
    }

    private fun TestScope.epochMs(offsetMs: Long = 0): Long =
        TestClock.ORIGIN_EPOCH_MS + testScheduler.currentTime + offsetMs

    private fun TestScope.activeTab(token: String = "token-a"): Tab =
        tab(StoredSession(token, epochMs(60.minutes.inWholeMilliseconds))).also {
            it.manager.start()
            runCurrent()
        }

    @Test
    fun restoresAValidSessionFromStorage() =
        runTest {
            val tab = tab(StoredSession("token-a", epochMs(60_000)))
            tab.manager.start()
            runCurrent()
            assertTrue(tab.manager.state.value is SessionState.Active)
            assertEquals("token-a", tab.manager.tokenForRequest())
            assertTrue(tab.channel.sent.isEmpty(), "no request when the tab has its own session")
        }

    @Test
    fun anExpiredStoredSessionIsDiscarded() =
        runTest {
            val tab = tab(StoredSession("token-a", epochMs()))
            tab.manager.start()
            runCurrent()
            assertNull(tab.storage.stored)
            assertEquals(listOf<TabMessage>(TabMessage.Request), tab.channel.sent)
        }

    @Test
    fun withoutStorageAsksOtherTabsAndAcceptsAShareWithin300Ms() =
        runTest {
            val other = activeTab()
            val tab = tab()
            tab.manager.start()
            assertEquals(SessionState.Resolving, tab.manager.state.value)
            runCurrent()

            assertEquals(listOf<TabMessage>(TabMessage.Request), tab.channel.sent)
            assertEquals("token-a", (other.channel.sent.single() as TabMessage.Share).accessToken)
            assertTrue(tab.manager.state.value is SessionState.Active)
            assertEquals("token-a", tab.storage.stored?.accessToken)
            assertEquals("token-a", tab.manager.tokenForRequest())
            advanceTimeBy(1_000)
            runCurrent()
            assertTrue(tab.manager.state.value is SessionState.Active)
        }

    @Test
    fun withoutAnswerIsAnonymousAfter300Ms() =
        runTest {
            val tab = tab()
            tab.manager.start()
            runCurrent()
            advanceTimeBy(299)
            runCurrent()
            assertEquals(SessionState.Resolving, tab.manager.state.value)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(SessionState.Anonymous(AnonymousReason.None), tab.manager.state.value)
            assertNull(tab.manager.tokenForRequest())
        }

    @Test
    fun aLateShareAfterResolvingStillLogsIn() =
        runTest {
            val tab = tab()
            tab.manager.start()
            advanceTimeBy(500)
            runCurrent()
            val probe = network.newTab()
            probe.send(TabMessage.Share("token-b", epochMs(60_000)))
            runCurrent()
            assertEquals("token-b", tab.manager.tokenForRequest())
            assertEquals(listOf<SessionEvent>(SessionEvent.LoggedIn), tab.events)
        }

    @Test
    fun answersARequestWhenActive() =
        runTest {
            val tab = activeTab("token-a")
            val probe = network.newTab()
            probe.send(TabMessage.Request)
            runCurrent()
            val share = tab.channel.sent.single() as TabMessage.Share
            assertEquals("token-a", share.accessToken)
            assertEquals(tab.storage.stored?.expiresAtEpochMs, share.expiresAtEpochMs)
        }

    @Test
    fun expiresExactlyAtTheExpiryInstant() =
        runTest {
            val tab = tab()
            tab.manager.start()
            advanceTimeBy(300)
            runCurrent()
            tab.manager.onLoggedIn("token-a", 60.minutes)
            runCurrent()
            advanceTimeBy(60.minutes - 1.milliseconds)
            runCurrent()
            assertTrue(tab.manager.state.value is SessionState.Active)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(SessionState.Anonymous(AnonymousReason.Expired), tab.manager.state.value)
            assertNull(tab.storage.stored)
            assertEquals(TabMessage.Expired, tab.channel.sent.last())
            assertEquals(listOf(SessionEvent.LoggedIn, SessionEvent.Expired), tab.events)
        }

    @Test
    fun tokenForRequestExpiresWhenTheClockIsPastTheExpiry() =
        runTest {
            val tab = activeTab()
            tab.clock.skew = 60.minutes
            assertNull(tab.manager.tokenForRequest())
            assertEquals(SessionState.Anonymous(AnonymousReason.Expired), tab.manager.state.value)
            assertNull(tab.storage.stored)
            assertEquals(TabMessage.Expired, tab.channel.sent.last())
            runCurrent()
            assertEquals(listOf<SessionEvent>(SessionEvent.Expired), tab.events)
        }

    @Test
    fun unauthorizedExpiresTheSession() =
        runTest {
            val tab = activeTab()
            tab.manager.onUnauthorized()
            runCurrent()
            assertEquals(SessionState.Anonymous(AnonymousReason.Expired), tab.manager.state.value)
            assertEquals(listOf<TabMessage>(TabMessage.Expired), tab.channel.sent)
            assertEquals(listOf<SessionEvent>(SessionEvent.Expired), tab.events)
            assertNull(tab.storage.stored)
        }

    @Test
    fun logoutClearsAndTellsTheOtherTabs() =
        runTest {
            val tab = activeTab()
            tab.manager.logout()
            runCurrent()
            assertEquals(SessionState.Anonymous(AnonymousReason.LoggedOut), tab.manager.state.value)
            assertEquals(listOf<TabMessage>(TabMessage.Logout), tab.channel.sent)
            assertEquals(listOf<SessionEvent>(SessionEvent.LoggedOut), tab.events)
            assertNull(tab.storage.stored)
            assertNull(tab.manager.tokenForRequest())
        }

    @Test
    fun receivedLogoutAndExpiredMessagesEndTheSessionWithoutEchoing() =
        runTest {
            val loggedOut = activeTab()
            val expired = activeTab()
            val probe = network.newTab()

            probe.send(TabMessage.Logout)
            runCurrent()
            assertEquals(SessionState.Anonymous(AnonymousReason.LoggedOut), loggedOut.manager.state.value)
            assertEquals(listOf<SessionEvent>(SessionEvent.LoggedOut), loggedOut.events)
            assertNull(loggedOut.storage.stored)

            expired.manager.onLoggedIn("token-c", 60.minutes)
            runCurrent()
            probe.send(TabMessage.Expired)
            runCurrent()
            assertEquals(SessionState.Anonymous(AnonymousReason.Expired), expired.manager.state.value)
            assertNull(expired.storage.stored)
            assertFalse(loggedOut.channel.sent.any { it == TabMessage.Logout || it == TabMessage.Expired })
        }

    @Test
    fun loginPublishesTheSession() =
        runTest {
            val tab = tab()
            val waiting = tab()
            tab.manager.start()
            waiting.manager.start()
            advanceTimeBy(300)
            runCurrent()
            assertEquals(SessionState.Anonymous(AnonymousReason.None), waiting.manager.state.value)

            tab.manager.onLoggedIn("token-a", 60.minutes)
            runCurrent()
            val share = tab.channel.sent.last() as TabMessage.Share
            assertEquals("token-a", share.accessToken)
            assertEquals(epochMs(60.minutes.inWholeMilliseconds), share.expiresAtEpochMs)
            assertEquals(StoredSession("token-a", share.expiresAtEpochMs), tab.storage.stored)
            assertEquals("token-a", waiting.manager.tokenForRequest())
            assertEquals(listOf<SessionEvent>(SessionEvent.LoggedIn), waiting.events)
        }

    @Test
    fun theTokenNeverAppearsInToString() =
        runTest {
            val tab = activeTab("secret-token")
            val active = tab.manager.state.value as SessionState.Active
            assertFalse(active.session.toString().contains("secret-token"))
            assertFalse(active.toString().contains("secret-token"))
            assertFalse(StoredSession("secret-token", 1).toString().contains("secret-token"))
            assertFalse(TabMessage.Share("secret-token", 1).toString().contains("secret-token"))
            assertEquals("Session(****)", active.session.toString())
        }
}

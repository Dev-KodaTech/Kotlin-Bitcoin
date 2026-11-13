package com.kotlinbitcoins.web.state.session

import com.kotlinbitcoins.web.state.port.Clock
import com.kotlinbitcoins.web.state.port.SessionStorage
import com.kotlinbitcoins.web.state.port.SessionTokenProvider
import com.kotlinbitcoins.web.state.port.StoredSession
import com.kotlinbitcoins.web.state.port.TabChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Session protocol (W-08): `sessionStorage` per tab, sharing between tabs through the [TabChannel], expiry at
 * `login instant + expiresIn` (timer and check before each request) and on 401. There is no renewal.
 * Every exit from [SessionState.Active] clears the storage, tells the other tabs when it started here and emits a
 * [SessionEvent] for the navigation.
 */
class SessionManager(
    private val storage: SessionStorage,
    private val tabChannel: TabChannel,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val requestTimeout: Duration = DEFAULT_REQUEST_TIMEOUT,
) : SessionTokenProvider {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Resolving)
    private val eventChannel = Channel<SessionEvent>(Channel.BUFFERED)
    private var expiryJob: Job? = null

    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    /** One-shot events for the navigation (single consumer). */
    val events: Flow<SessionEvent> = eventChannel.receiveAsFlow()

    fun start() {
        // Undispatched: the listener is in place before the request below goes out.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { tabChannel.messages.collect(::onMessage) }

        val stored = storage.read()
        if (stored != null && isValid(stored.expiresAtEpochMs)) {
            activate(Session(stored.accessToken, Instant.fromEpochMilliseconds(stored.expiresAtEpochMs)))
            return
        }
        if (stored != null) storage.clear()
        tabChannel.send(TabMessage.Request)
        scope.launch {
            delay(requestTimeout)
            if (mutableState.value == SessionState.Resolving) {
                mutableState.value = SessionState.Anonymous(AnonymousReason.None)
            }
        }
    }

    /** Successful login in this tab. */
    fun onLoggedIn(
        accessToken: String,
        expiresIn: Duration,
    ) {
        val expiresAt = clock.now() + expiresIn
        val stored = StoredSession(accessToken, expiresAt.toEpochMilliseconds())
        storage.write(stored)
        tabChannel.send(TabMessage.Share(stored.accessToken, stored.expiresAtEpochMs))
        activate(Session(accessToken, expiresAt))
        eventChannel.trySend(SessionEvent.LoggedIn)
    }

    override fun tokenForRequest(): String? {
        val session = (mutableState.value as? SessionState.Active)?.session
        if (session != null && clock.now() >= session.expiresAt) {
            end(AnonymousReason.Expired, broadcast = true)
            return null
        }
        return session?.accessToken
    }

    override fun onUnauthorized() {
        end(AnonymousReason.Expired, broadcast = true)
    }

    fun logout() {
        end(AnonymousReason.LoggedOut, broadcast = true)
    }

    private fun onMessage(message: TabMessage) {
        when (message) {
            TabMessage.Request -> shareCurrent()
            is TabMessage.Share -> adopt(message)
            TabMessage.Logout -> end(AnonymousReason.LoggedOut, broadcast = false)
            TabMessage.Expired -> end(AnonymousReason.Expired, broadcast = false)
        }
    }

    private fun shareCurrent() {
        val session = (mutableState.value as? SessionState.Active)?.session ?: return
        tabChannel.send(TabMessage.Share(session.accessToken, session.expiresAt.toEpochMilliseconds()))
    }

    private fun adopt(share: TabMessage.Share) {
        val previous = mutableState.value
        if (previous is SessionState.Active || !isValid(share.expiresAtEpochMs)) return
        storage.write(StoredSession(share.accessToken, share.expiresAtEpochMs))
        activate(Session(share.accessToken, Instant.fromEpochMilliseconds(share.expiresAtEpochMs)))
        if (previous is SessionState.Anonymous) eventChannel.trySend(SessionEvent.LoggedIn)
    }

    private fun activate(session: Session) {
        mutableState.value = SessionState.Active(session)
        expiryJob?.cancel()
        expiryJob =
            scope.launch {
                delay(session.expiresAt - clock.now())
                end(AnonymousReason.Expired, broadcast = true)
            }
    }

    private fun end(
        reason: AnonymousReason,
        broadcast: Boolean,
    ) {
        if (mutableState.value !is SessionState.Active) return
        val job = expiryJob
        expiryJob = null
        storage.clear()
        mutableState.value = SessionState.Anonymous(reason)
        val (message, event) =
            when (reason) {
                AnonymousReason.LoggedOut -> TabMessage.Logout to SessionEvent.LoggedOut
                AnonymousReason.Expired, AnonymousReason.None -> TabMessage.Expired to SessionEvent.Expired
            }
        if (broadcast) tabChannel.send(message)
        eventChannel.trySend(event)
        job?.cancel()
    }

    private fun isValid(expiresAtEpochMs: Long): Boolean = clock.now() < Instant.fromEpochMilliseconds(expiresAtEpochMs)

    companion object {
        val DEFAULT_REQUEST_TIMEOUT: Duration = 300.milliseconds
    }
}

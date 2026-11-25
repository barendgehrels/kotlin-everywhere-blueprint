package org.example.slippy.foundation

import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.slippy.resources.Res
import org.example.slippy.resources.error_session_expired

/**
 * The session, shared by all screens. It restores the session at startup and refreshes it then;
 * later, a token is refreshed when a call needs it ([validIdToken]), never on a timer.
 * Only the service's session-expired code logs the user out.
 */
class SessionStore(
    private val gateway: SessionGateway,
    private val messages: MessageStore,
    private val storage: LocalStorage,
    private val scope: CoroutineScope,
    private val humanCheck: suspend () -> String?,
    private val nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds },
) {
    val session: StateFlow<Session>
        field = MutableStateFlow(Session())

    private val refreshMutex = Mutex()

    init {
        scope.launch {
            storage.get(StorageEntries.SESSION)?.let { stored ->
                // Not when the user logged in meanwhile
                if (!session.value.hasSession) session.value = stored
            }
            // Also brings the email verification up to date
            if (session.value.hasSession) refreshNow().onFailure { if (!isSessionExpired(it)) messages.show(it) }
        }
    }

    fun setSession(newSession: Session?) {
        val value = newSession ?: Session()
        session.value = value
        scope.launch { storage.set(StorageEntries.SESSION, value) }
    }

    /** The ID token for a call, refreshed first when it expires soon; null without a session. */
    suspend fun validIdToken(): String? {
        val idToken = session.value.idToken ?: return null
        if (!tokenExpiresSoon(idToken, nowEpochSeconds())) return idToken
        refreshMutex.withLock {
            // Refreshed meanwhile, by another call
            val current = session.value.idToken ?: return null
            if (!tokenExpiresSoon(current, nowEpochSeconds())) return current
            refresh()
        }
        return session.value.idToken
    }

    /** Refreshes now, such as before a guest registers, or to see the email verification. */
    suspend fun refreshNow(): Result<Unit> = refreshMutex.withLock { refresh() }

    /**
     * A call with the token, for the gateways: without a session it fails with
     * [NotLoggedInException], and a session-expired answer logs the user out.
     */
    suspend fun <T> withIdToken(call: suspend (idToken: String) -> Result<T>): Result<T> {
        val idToken = validIdToken() ?: return Result.failure(NotLoggedInException())
        val used = session.value
        return call(idToken).onFailure { if (isSessionExpired(it)) expire(used) }
    }

    /** Logs out; a guest is remembered on this device, to resume it by name. */
    fun logOut() {
        val current = session.value
        val refreshToken = current.refreshToken
        val name = current.name
        setSession(null)
        if (current.isGuest && refreshToken != null && name != null) {
            scope.launch { rememberGuest(KnownGuest(name, refreshToken)) }
        }
    }

    /**
     * A guest: the same name as a guest that logged out on this device resumes it, without a human
     * check. Otherwise a new guest, after the human check.
     */
    suspend fun startGuest(name: String): Result<Unit> {
        val known = knownGuests().firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (known != null) {
            val resumed = gateway.refresh(known.refreshToken)
            resumed.onSuccess { fresh ->
                forgetGuest(known.name)
                setSession(fresh.copy(isGuest = true, name = fresh.name ?: known.name))
                return Result.success(Unit)
            }
            val error = resumed.exceptionOrNull()!!
            // Offline, the entry stays for next time; an expired one means a new guest
            if (!isSessionExpired(error)) return Result.failure(error)
            forgetGuest(known.name)
        }
        val humanToken = humanCheck() ?: return Result.failure(NoHumanCheckException())
        return gateway.guest(humanToken, name).map { setSession(it) }
    }

    private suspend fun refresh(): Result<Unit> {
        val before = session.value
        val refreshToken = before.refreshToken ?: return Result.failure(NotLoggedInException())
        return gateway.refresh(refreshToken).fold(
            onSuccess = { fresh ->
                // Overtaken: the user logged out, or in again, while the refresh was on its way
                if (session.value == before) {
                    setSession(fresh.copy(email = before.email, name = fresh.name ?: before.name))
                }
                Result.success(Unit)
            },
            onFailure = { error ->
                if (isSessionExpired(error)) expire(before)
                Result.failure(error)
            },
        )
    }

    /** The session is gone: log out, unless it was replaced meanwhile. */
    private fun expire(before: Session) {
        if (session.value != before || !before.hasSession) return
        setSession(null)
        messages.show(Res.string.error_session_expired, isError = true)
    }

    private fun isSessionExpired(error: Throwable) =
        error is ApiException && error.code == ERROR_CODE_SESSION_EXPIRED

    private suspend fun knownGuests(): List<KnownGuest> = storage.get(StorageEntries.KNOWN_GUESTS).orEmpty()

    private suspend fun rememberGuest(guest: KnownGuest) {
        val others = knownGuests().filterNot { it.name.equals(guest.name, ignoreCase = true) }
        storage.set(StorageEntries.KNOWN_GUESTS, others + guest)
    }

    private suspend fun forgetGuest(name: String) {
        storage.set(StorageEntries.KNOWN_GUESTS, knownGuests().filterNot { it.name.equals(name, ignoreCase = true) })
    }
}

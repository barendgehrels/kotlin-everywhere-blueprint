package org.example.slippy.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStoreTest {
    private val now = 1_800_000_000L
    private val gateway = FakeSessionGateway()
    private val messages = MessageStore()
    private val storage = LocalStorage(MemoryKeyValueStore())

    private fun TestScope.store(humanToken: String? = "human") =
        SessionStore(gateway, messages, storage, storeScope(), { humanToken }, { now })

    @Test
    fun expiringTokenIsRefreshedBeforeTheCall() = runTest {
        val store = store()
        store.setSession(Session(idToken = testToken(now + 60), refreshToken = "r1", email = "a@b.nl"))
        gateway.refreshResult = Result.success(Session(idToken = testToken(now + 3600), refreshToken = "r2"))
        assertEquals(testToken(now + 3600), store.validIdToken())
        assertEquals("a@b.nl", store.session.value.email)        // the store keeps the email
    }

    @Test
    fun overtakenRefreshIsDropped() = runTest {
        val store = store()
        store.setSession(Session(idToken = testToken(now + 60), refreshToken = "r1"))
        val call = async { store.validIdToken() }
        advanceUntilIdle()
        // The user logs out while the refresh is on its way
        store.logOut()
        gateway.refreshes.single().second.complete(Result.success(Session(idToken = testToken(now + 3600), refreshToken = "r2")))
        call.await()
        assertFalse(store.session.value.hasSession)
    }

    @Test
    fun onlySessionExpiredLogsOut() = runTest {
        val store = store()
        val session = Session(idToken = testToken(now + 60), refreshToken = "r1")
        store.setSession(session)
        gateway.refreshResult = Result.failure(NetworkException(Exception("offline")))
        store.validIdToken()
        assertEquals(session, store.session.value)

        gateway.refreshResult = Result.failure(ApiException(ERROR_CODE_SESSION_EXPIRED, "gone", 400))
        store.validIdToken()
        assertFalse(store.session.value.hasSession)
        assertEquals(1, messages.messages.value.size)
    }

    @Test
    fun guestWhoLoggedOutResumesByNameWithoutHumanCheck() = runTest {
        val store = store()
        assertTrue(store.startGuest("Explorer").isSuccess)
        assertEquals(1, gateway.guestCalls.size)
        store.logOut()
        advanceUntilIdle()

        gateway.refreshResult = Result.success(Session(idToken = "resumed", refreshToken = "r3", isGuest = true))
        assertTrue(store.startGuest("explorer").isSuccess)          // case doesn't matter
        assertEquals(1, gateway.guestCalls.size)                     // no new guest
        assertEquals("resumed", store.session.value.idToken)
        assertEquals("Explorer", store.session.value.name)
    }

    @Test
    fun newGuestWithoutHumanCheckFails() = runTest {
        val store = store(humanToken = null)
        assertTrue(store.startGuest("Explorer").exceptionOrNull() is NoHumanCheckException)
        assertNull(store.session.value.idToken)
    }
}

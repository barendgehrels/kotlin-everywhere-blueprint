package org.example.slippy.foundation

import kotlin.io.encoding.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * The app-wide scope of a store, on the test's scheduler. Not backgroundScope: advanceUntilIdle
 * does not run background work, and a store's saves must run.
 */
fun TestScope.storeScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

/** A JWT with only `exp` in its payload: enough for the app, which never checks the signature. */
fun testToken(exp: Long, subject: String = "user"): String {
    val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    val header = encoder.encode("""{"alg":"none"}""".encodeToByteArray())
    val payload = encoder.encode("""{"sub":"$subject","exp":$exp}""".encodeToByteArray())
    return "$header.$payload.signature"
}

/** Answers from a queue of deferreds, so a test decides when (and in which order) they arrive. */
class FakeSessionGateway : SessionGateway {
    val refreshes = mutableListOf<Pair<String, CompletableDeferred<Result<Session>>>>()
    var guestResult: Result<Session> = Result.success(Session(idToken = "guest-id", refreshToken = "guest-refresh", isGuest = true, name = "Explorer"))
    val guestCalls = mutableListOf<Pair<String, String>>()

    /** When set, refresh answers at once with this. */
    var refreshResult: Result<Session>? = null

    override suspend fun refresh(refreshToken: String): Result<Session> {
        refreshResult?.let { return it }
        val answer = CompletableDeferred<Result<Session>>()
        refreshes += refreshToken to answer
        return answer.await()
    }

    override suspend fun guest(humanToken: String, name: String): Result<Session> {
        guestCalls += humanToken to name
        return guestResult
    }
}

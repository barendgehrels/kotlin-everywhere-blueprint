package org.example.slippy.foundation

import org.example.slippy.account.AccountResponse
import org.example.slippy.account.GuestRequest
import org.example.slippy.account.RefreshTokenRequest

/** The calls the SessionStore makes itself. */
interface SessionGateway {
    /** New tokens; the session has no email (the store keeps its own). */
    suspend fun refresh(refreshToken: String): Result<Session>

    suspend fun guest(humanToken: String, name: String): Result<Session>
}

class CloudSessionGateway : SessionGateway {
    override suspend fun refresh(refreshToken: String): Result<Session> =
        NetworkClient.postJson<RefreshTokenRequest, AccountResponse>(
            "refresh_token", RefreshTokenRequest(refreshToken)
        ).map { it.toSession(email = null) }

    override suspend fun guest(humanToken: String, name: String): Result<Session> =
        NetworkClient.postJson<GuestRequest, AccountResponse>(
            "guest", GuestRequest(humanToken, name)
        ).map { it.toSession(email = null) }
}

package org.example.slippy.account

import org.example.slippy.authentication.ExpectedError
import org.example.slippy.authentication.refreshTokens
import org.example.slippy.authentication.verifyIdToken
import org.example.slippy.foundation.Call
import org.example.slippy.foundation.ERROR_CODE_REFRESH_TOKEN
import org.example.slippy.foundation.ERROR_CODE_SESSION_EXPIRED
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.decodeBody
import org.example.slippy.foundation.findUser
import org.example.slippy.foundation.jsonResponse
import org.example.slippy.foundation.requireDb
import org.example.slippy.foundation.syncRegisteredUser
import org.example.slippy.foundation.touchUser
import web.http.Response

/** Firebase refresh tokens are far shorter. */
private const val REFRESH_TOKEN_MAX_LENGTH = 2048

private val refreshErrors = listOf(
    "TOKEN_EXPIRED", "USER_DISABLED", "USER_NOT_FOUND", "INVALID_REFRESH_TOKEN", "MISSING_REFRESH_TOKEN",
).associateWith { ExpectedError(ERROR_CODE_SESSION_EXPIRED) }

/** New tokens; also resumes a guest that the device remembered. */
suspend fun handleRefreshToken(call: Call): Response {
    val body = call.request.decodeBody<RefreshTokenRequest>()
    if (body.refreshToken.isBlank() || body.refreshToken.length > REFRESH_TOKEN_MAX_LENGTH) {
        throw ServiceException(ERROR_CODE_REFRESH_TOKEN, "Invalid refresh token", 400)
    }
    val tokens = refreshTokens(call.env, body.refreshToken, refreshErrors)
    val payload = verifyIdToken(call.env, tokens.idToken)
    val db = requireDb(call.env)
    if (payload.isAnonymous) {
        // Only a guest that /guest made: any other anonymous user is no session of ours
        val guest = findUser(db, payload.sub)?.takeIf { it.isGuest }
            ?: throw ServiceException(ERROR_CODE_SESSION_EXPIRED, "Session expired", 400)
        touchUser(db, guest.id)
        return jsonResponse(accountResponse(tokens, payload, isGuest = true, name = guest.name))
    }
    syncRegisteredUser(db, payload.sub, payload.email, payload.emailVerified)
    return jsonResponse(accountResponse(tokens, payload))
}

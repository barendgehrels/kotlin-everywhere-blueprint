package org.example.slippy.foundation

import org.example.slippy.authentication.JwtPayload
import org.example.slippy.authentication.verifyIdToken

// Who may do what, decided per endpoint (BLUE_PRINT_GUEST.md, "What a guest may do").

/** Any valid token, a guest's too. A missing or invalid token is a 401. */
suspend fun requireSession(call: Call): JwtPayload {
    call.session?.let { return it }
    val token = call.token ?: throw ServiceException(ERROR_CODE_TOKEN_VALIDITY, "Missing token", 401)
    return verifyIdToken(call.env, token).also { call.session = it }
}

/** Our user, with its row. A guest only with [allowGuest]; otherwise a 403 (GUEST). */
suspend fun requireUser(call: Call, allowGuest: Boolean = false): User {
    val session = requireSession(call)
    val user = call.user ?: loadUser(call.env, session).also { call.user = it }
    if (user.isGuest && !allowGuest) throw ServiceException(ERROR_CODE_GUEST, "Register first", 403)
    return user
}

suspend fun requireUserId(call: Call): Int = requireUser(call).id

/** A registered user with a verified email address. */
suspend fun requireVerifiedUserId(call: Call): Int {
    val user = requireUser(call)
    if (!requireSession(call).emailVerified) {
        throw ServiceException(ERROR_CODE_EMAIL_NOT_VERIFIED, "Verify your email address first", 403)
    }
    return user.id
}

/**
 * For what costs money: a guest within its guest budget, a registered user only with a verified
 * email, both within their budget, and the rate limit per user.
 */
suspend fun guardRequest(call: Call): Int {
    val user = requireUser(call, allowGuest = true)
    if (!user.isGuest && !requireSession(call).emailVerified) {
        throw ServiceException(ERROR_CODE_EMAIL_NOT_VERIFIED, "Verify your email address first", 403)
    }
    requireWithinBudget(call, user)
    requireWithinLimit(call.env.USER_LIMITER, "USER_LIMITER", "user:${user.id}")
    return user.id
}

/**
 * The row of the token's user. A registered user gets one at the first call; an anonymous
 * token without a row was not made by /guest, so it is no session of ours.
 */
private suspend fun loadUser(env: Env, session: JwtPayload): User {
    val db = requireDb(env)
    findUser(db, session.sub)?.let { return it }
    if (session.isAnonymous) throw ServiceException(ERROR_CODE_SESSION_EXPIRED, "Session expired", 401)
    return syncRegisteredUser(db, session.sub, session.email, session.emailVerified)
}

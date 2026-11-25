package org.example.slippy.account

import org.example.slippy.authentication.ExpectedError
import org.example.slippy.authentication.signUpWithPassword
import org.example.slippy.authentication.updateAccount
import org.example.slippy.authentication.verifyIdToken
import org.example.slippy.foundation.Call
import org.example.slippy.foundation.ERROR_CODE_REGISTER
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.decodeBody
import org.example.slippy.foundation.jsonResponse
import org.example.slippy.foundation.requireDb
import org.example.slippy.foundation.requireSession
import org.example.slippy.foundation.requireWithinIpLimit
import org.example.slippy.foundation.syncRegisteredUser
import web.http.Response

private val registerErrors = listOf(
    "EMAIL_EXISTS", "INVALID_EMAIL", "WEAK_PASSWORD", "MISSING_PASSWORD", "CREDENTIAL_TOO_OLD_LOGIN_AGAIN",
).associateWith { ExpectedError(ERROR_CODE_REGISTER) }

/** A new account; with a guest's bearer token, the guest becomes the account (same uid). */
suspend fun handleRegister(call: Call): Response {
    requireWithinIpLimit(call, call.env.ACCOUNT_LIMITER, "ACCOUNT_LIMITER")
    val body = call.request.decodeBody<RegisterRequest>()
    if (!isEmailValid(body.email) || !isPasswordValid(body.password)) {
        throw ServiceException(ERROR_CODE_REGISTER, "Invalid email address or password", 400)
    }
    // A token that is there must be valid: else a guest would silently lose what it made
    val guestToken = call.token?.takeIf { requireSession(call).isAnonymous }
    val tokens = if (guestToken != null) {
        updateAccount(call.env, guestToken, body.email, body.password, registerErrors)
    } else {
        signUpWithPassword(call.env, body.email, body.password, registerErrors)
    }
    val payload = verifyIdToken(call.env, tokens.idToken)
    syncRegisteredUser(requireDb(call.env), payload.sub, body.email, payload.emailVerified)
    return jsonResponse(accountResponse(tokens, payload))
}

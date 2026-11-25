package org.example.slippy.account

import org.example.slippy.authentication.ExpectedError
import org.example.slippy.authentication.signInWithPassword
import org.example.slippy.authentication.verifyIdToken
import org.example.slippy.foundation.Call
import org.example.slippy.foundation.ERROR_CODE_LOGIN
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.decodeBody
import org.example.slippy.foundation.jsonResponse
import org.example.slippy.foundation.requireDb
import org.example.slippy.foundation.requireWithinIpLimit
import org.example.slippy.foundation.syncRegisteredUser
import web.http.Response

private val loginErrors = listOf(
    "INVALID_LOGIN_CREDENTIALS", "EMAIL_NOT_FOUND", "INVALID_PASSWORD", "USER_DISABLED", "INVALID_EMAIL",
).associateWith { ExpectedError(ERROR_CODE_LOGIN) }

suspend fun handleLogin(call: Call): Response {
    requireWithinIpLimit(call, call.env.LOGIN_LIMITER, "LOGIN_LIMITER")
    val body = call.request.decodeBody<LoginRequest>()
    if (!isEmailValid(body.email) || body.password.length > PASSWORD_MAX_LENGTH) {
        throw ServiceException(ERROR_CODE_LOGIN, "Wrong email address or password", 400)
    }
    val tokens = signInWithPassword(call.env, body.email, body.password, loginErrors)
    val payload = verifyIdToken(call.env, tokens.idToken)
    syncRegisteredUser(requireDb(call.env), payload.sub, payload.email, payload.emailVerified)
    return jsonResponse(accountResponse(tokens, payload))
}

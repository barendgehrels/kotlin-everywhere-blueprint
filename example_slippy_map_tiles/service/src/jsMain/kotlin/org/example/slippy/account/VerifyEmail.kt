package org.example.slippy.account

import org.example.slippy.authentication.ExpectedError
import org.example.slippy.authentication.sendOobCode
import org.example.slippy.foundation.Call
import org.example.slippy.foundation.ENV_CONFIRM_URL
import org.example.slippy.foundation.ERROR_CODE_SEND_MAIL
import org.example.slippy.foundation.ERROR_CODE_SESSION_EXPIRED
import org.example.slippy.foundation.jsonResponse
import org.example.slippy.foundation.requireEnv
import org.example.slippy.foundation.requireUser
import org.example.slippy.foundation.requireWithinIpLimit
import web.http.Response

private val verifyEmailErrors = mapOf(
    "INVALID_ID_TOKEN" to ExpectedError(ERROR_CODE_SESSION_EXPIRED, 401),
    "USER_NOT_FOUND" to ExpectedError(ERROR_CODE_SESSION_EXPIRED, 401),
    "EMAIL_NOT_FOUND" to ExpectedError(ERROR_CODE_SEND_MAIL),
    "INVALID_EMAIL" to ExpectedError(ERROR_CODE_SEND_MAIL),
)

/** Firebase sends the verification email; its link ends on CONFIRM_URL. */
suspend fun handleVerifyEmail(call: Call): Response {
    requireWithinIpLimit(call, call.env.ACCOUNT_LIMITER, "ACCOUNT_LIMITER")
    requireUser(call)
    val continueUrl = requireEnv(call.env.CONFIRM_URL, ENV_CONFIRM_URL)
    sendOobCode(call.env, "VERIFY_EMAIL", idToken = call.token, email = null, continueUrl, verifyEmailErrors)
    return jsonResponse(EmptyResponse())
}

package org.example.slippy.account

import org.example.slippy.authentication.ExpectedError
import org.example.slippy.authentication.sendOobCode
import org.example.slippy.foundation.Call
import org.example.slippy.foundation.ERROR_CODE_RESET_PASSWORD
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.decodeBody
import org.example.slippy.foundation.jsonResponse
import org.example.slippy.foundation.requireWithinIpLimit
import web.http.Response

private val resetErrors = listOf("EMAIL_NOT_FOUND", "INVALID_EMAIL", "USER_DISABLED")
    .associateWith { ExpectedError(ERROR_CODE_RESET_PASSWORD) }

/** Firebase sends the reset email. */
suspend fun handlePasswordReset(call: Call): Response {
    requireWithinIpLimit(call, call.env.ACCOUNT_LIMITER, "ACCOUNT_LIMITER")
    val body = call.request.decodeBody<PasswordResetRequest>()
    if (!isEmailValid(body.email)) throw ServiceException(ERROR_CODE_RESET_PASSWORD, "Invalid email address", 400)
    sendOobCode(call.env, "PASSWORD_RESET", idToken = null, email = body.email, continueUrl = null,
        expected = resetErrors)
    return jsonResponse(EmptyResponse())
}

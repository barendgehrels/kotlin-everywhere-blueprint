package org.example.slippy.account

import org.example.slippy.authentication.ExpectedError
import org.example.slippy.authentication.updateAccount
import org.example.slippy.authentication.verifyIdToken
import org.example.slippy.foundation.Call
import org.example.slippy.foundation.ERROR_CODE_CHANGE_PASSWORD
import org.example.slippy.foundation.ERROR_CODE_GUEST
import org.example.slippy.foundation.ERROR_CODE_SESSION_EXPIRED
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.decodeBody
import org.example.slippy.foundation.jsonResponse
import web.http.Response

private val changePasswordErrors = mapOf(
    "WEAK_PASSWORD" to ExpectedError(ERROR_CODE_CHANGE_PASSWORD),
    "CREDENTIAL_TOO_OLD_LOGIN_AGAIN" to ExpectedError(ERROR_CODE_CHANGE_PASSWORD),
    "INVALID_ID_TOKEN" to ExpectedError(ERROR_CODE_SESSION_EXPIRED, 401),
    "TOKEN_EXPIRED" to ExpectedError(ERROR_CODE_SESSION_EXPIRED, 401),
    "USER_NOT_FOUND" to ExpectedError(ERROR_CODE_SESSION_EXPIRED, 401),
)

/** A new password, and new tokens: the old ones stop working. */
suspend fun handleChangePassword(call: Call): Response {
    val body = call.request.decodeBody<ChangePasswordRequest>()
    if (!isPasswordValid(body.password)) throw ServiceException(ERROR_CODE_CHANGE_PASSWORD, "Invalid password", 400)
    if (verifyIdToken(call.env, body.idToken).isAnonymous) {
        throw ServiceException(ERROR_CODE_GUEST, "Register first", 403)
    }
    val tokens = updateAccount(call.env, body.idToken, email = null, password = body.password, changePasswordErrors)
    val payload = verifyIdToken(call.env, tokens.idToken)
    return jsonResponse(accountResponse(tokens, payload))
}

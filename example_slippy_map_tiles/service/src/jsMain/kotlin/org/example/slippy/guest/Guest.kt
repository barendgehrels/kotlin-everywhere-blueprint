package org.example.slippy.guest

import org.example.slippy.account.AccountResponse
import org.example.slippy.account.GuestRequest
import org.example.slippy.account.isGuestNameValid
import org.example.slippy.authentication.requireHuman
import org.example.slippy.authentication.signUpAnonymously
import org.example.slippy.foundation.Call
import org.example.slippy.foundation.ENV_GUEST_DAILY_MAX
import org.example.slippy.foundation.ERROR_CODE_GUEST_NAME
import org.example.slippy.foundation.ERROR_CODE_TOO_MANY_REQUESTS
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.countGuestsSince
import org.example.slippy.foundation.decodeBody
import org.example.slippy.foundation.insertGuest
import org.example.slippy.foundation.isoTime
import org.example.slippy.foundation.jsonResponse
import org.example.slippy.foundation.nowMillis
import org.example.slippy.foundation.requireDb
import org.example.slippy.foundation.requireIntEnv
import org.example.slippy.foundation.requireWithinIpLimit
import org.example.slippy.foundation.startOfDayMillis
import web.http.Response

/** POST /guest: a new guest, an anonymous Firebase user with a row of ours (BLUE_PRINT_GUEST.md, 4). */
suspend fun handleGuest(call: Call): Response {
    requireWithinIpLimit(call, call.env.GUEST_LIMITER, "GUEST_LIMITER")
    val body = call.request.decodeBody<GuestRequest>()
    // Before the human check, so a wrong name doesn't use up a token
    if (!isGuestNameValid(body.name)) throw ServiceException(ERROR_CODE_GUEST_NAME, "Invalid guest name", 400)
    val db = requireDb(call.env)
    val dailyMax = requireIntEnv(call.env.GUEST_DAILY_MAX, ENV_GUEST_DAILY_MAX)
    if (countGuestsSince(db, isoTime(startOfDayMillis(nowMillis()))) >= dailyMax) {
        console.error("GUEST_DAILY_MAX ($dailyMax) reached")
        throw ServiceException(ERROR_CODE_TOO_MANY_REQUESTS, "No new guests today", 429)
    }
    requireHuman(call, body.humanToken)
    val tokens = signUpAnonymously(call.env)
    // The row before the answer, so what the guest uses is counted from the start
    insertGuest(db, tokens.localId, body.name)
    return jsonResponse(AccountResponse(tokens.idToken, tokens.refreshToken, emailVerified = false,
        isGuest = true, name = body.name))
}

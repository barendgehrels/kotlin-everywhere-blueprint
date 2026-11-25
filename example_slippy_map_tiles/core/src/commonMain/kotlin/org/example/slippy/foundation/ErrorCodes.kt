package org.example.slippy.foundation

import kotlinx.serialization.Serializable

/** The body of every error answer of the service. The app decides on [code], never on the message. */
@Serializable
data class ErrorBody(val code: Int, val errorMessage: String)

// General. A new code goes at the end of its group.
const val ERROR_CODE_GENERAL = 1                // 400, 404, 405, 500
const val ERROR_CODE_PROVIDER = 2               // 502: an AI or other provider failed
const val ERROR_CODE_BUDGET_EXCEEDED = 3        // 429: the weekly budget is used up
const val ERROR_CODE_ENVIRONMENT = 4            // 500, 503: a var, secret or binding is missing
const val ERROR_CODE_TOTAL_BUDGET_EXCEEDED = 5  // 429: the total budget is used up

// Accounts
const val ERROR_CODE_REFRESH_TOKEN = 101        // 400: the refresh failed
const val ERROR_CODE_SESSION_EXPIRED = 102      // 400, 401: the app logs out (the only code that does)
const val ERROR_CODE_TOKEN_VALIDITY = 103       // 401: the ID token is missing or invalid
const val ERROR_CODE_SEND_MAIL = 104            // 400: an email could not be sent
const val ERROR_CODE_REGISTER = 105             // 400: the registration was refused
const val ERROR_CODE_LOGIN = 106                // 400: a wrong email address or password
const val ERROR_CODE_CHANGE_PASSWORD = 107      // 400: the new password was refused
const val ERROR_CODE_RESET_PASSWORD = 108       // 400: the password reset was refused
const val ERROR_CODE_AUTHENTICATION = 109       // 502: Firebase itself failed
const val ERROR_CODE_GUEST = 110                // 403: a guest calls what is for registered users
const val ERROR_CODE_HUMAN_CHECK = 111          // 403: a new guest without a passed human check
const val ERROR_CODE_GUEST_NAME = 112           // 400: a guest name of the wrong length or form
const val ERROR_CODE_EMAIL_NOT_VERIFIED = 113   // 403: a call that costs money, before verification
const val ERROR_CODE_TOO_MANY_REQUESTS = 114    // 429: over a rate limit, ours or Firebase's
const val ERROR_CODE_GUEST_QUOTA = 115          // 429: a guest's quota of today is used up

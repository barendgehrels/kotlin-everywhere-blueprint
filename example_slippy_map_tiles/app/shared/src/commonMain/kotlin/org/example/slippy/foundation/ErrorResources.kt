package org.example.slippy.foundation

import org.example.slippy.resources.*
import org.jetbrains.compose.resources.StringResource

/** The app's own text for a service error code: the app decides on the code, never on the message. */
fun errorResource(code: Int): StringResource = when (code) {
    ERROR_CODE_PROVIDER -> Res.string.error_provider
    ERROR_CODE_BUDGET_EXCEEDED -> Res.string.error_budget_exceeded
    ERROR_CODE_ENVIRONMENT -> Res.string.error_environment
    ERROR_CODE_TOTAL_BUDGET_EXCEEDED -> Res.string.error_total_budget_exceeded
    ERROR_CODE_REFRESH_TOKEN -> Res.string.error_refresh_token
    ERROR_CODE_SESSION_EXPIRED -> Res.string.error_session_expired
    ERROR_CODE_TOKEN_VALIDITY -> Res.string.error_token_validity
    ERROR_CODE_SEND_MAIL -> Res.string.error_send_mail
    ERROR_CODE_REGISTER -> Res.string.error_register
    ERROR_CODE_LOGIN -> Res.string.error_login
    ERROR_CODE_CHANGE_PASSWORD -> Res.string.error_change_password
    ERROR_CODE_RESET_PASSWORD -> Res.string.error_reset_password
    ERROR_CODE_AUTHENTICATION -> Res.string.error_authentication
    ERROR_CODE_GUEST -> Res.string.error_guest
    ERROR_CODE_HUMAN_CHECK -> Res.string.error_human_check
    ERROR_CODE_GUEST_NAME -> Res.string.error_guest_name
    ERROR_CODE_EMAIL_NOT_VERIFIED -> Res.string.error_email_not_verified
    ERROR_CODE_TOO_MANY_REQUESTS -> Res.string.error_too_many_requests
    ERROR_CODE_GUEST_QUOTA -> Res.string.error_guest_quota
    else -> Res.string.error_general
}

package org.example.slippy.foundation

import org.example.slippy.resources.Res
import org.example.slippy.resources.error_human_check_unavailable
import org.example.slippy.resources.error_network
import org.example.slippy.resources.error_not_logged_in
import org.jetbrains.compose.resources.StringResource

/** An error answer of the service: [code] is one of core's ERROR_CODE_*. */
class ApiException(val code: Int, message: String, val status: Int) : Exception(message)

/** A failure with a localized text of the app. */
open class ExceptionWithStringResource(val resId: StringResource, cause: Throwable? = null) :
    Exception(cause?.message, cause)

class NotLoggedInException : ExceptionWithStringResource(Res.string.error_not_logged_in)

/** The service could not be reached, or answered something unreadable. */
class NetworkException(cause: Throwable) : ExceptionWithStringResource(Res.string.error_network, cause)

/** This platform has no human check, so no new guest can start here. */
class NoHumanCheckException : ExceptionWithStringResource(Res.string.error_human_check_unavailable)

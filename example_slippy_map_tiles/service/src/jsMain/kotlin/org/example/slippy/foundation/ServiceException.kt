package org.example.slippy.foundation

import web.http.Response

/** What a handler throws; the router turns it into the error response, in one place. */
class ServiceException(val code: Int, message: String, val status: Int) : Exception(message)

/** The error answer. A status of 500 or more is an error in the log, the rest a warning. */
fun createError(code: Int, message: String, status: Int): Response {
    if (status >= 500) console.error("$status ($code): $message") else console.warn("$status ($code): $message")
    return jsonResponse(ErrorBody(code, message), status)
}

package org.example.slippy.foundation

import org.example.slippy.cloudflare.D1Database

/** A var or secret that must be there. Its name goes to the log, not to the user. */
fun requireEnv(value: String?, name: String): String {
    if (value.isNullOrBlank()) {
        console.error("Missing var or secret $name")
        throw ServiceException(ERROR_CODE_ENVIRONMENT, "Configuration error", 500)
    }
    return value
}

/** A var that must be a whole number (a budget, a quota). */
fun requireIntEnv(value: String?, name: String): Int =
    requireEnv(value, name).trim().toIntOrNull() ?: run {
        console.error("Var $name is not a number")
        throw ServiceException(ERROR_CODE_ENVIRONMENT, "Configuration error", 500)
    }

/** A missing binding is a 503, so a forgotten binding never opens anything. */
fun requireDb(env: Env): D1Database = env.DB ?: run {
    console.error("Missing binding DB")
    throw ServiceException(ERROR_CODE_ENVIRONMENT, "Service unavailable", 503)
}

/** Every text that goes to a provider or the database has a maximum length. */
fun requireMaxLength(text: String, maxLength: Int, code: Int = ERROR_CODE_GENERAL) {
    if (text.length > maxLength) throw ServiceException(code, "Input too long", 400)
}

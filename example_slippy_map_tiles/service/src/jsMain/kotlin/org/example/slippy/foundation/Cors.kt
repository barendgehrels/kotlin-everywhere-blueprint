package org.example.slippy.foundation

/** The local web app (wasmJsBrowserDevelopmentRun) is always allowed. */
const val LOCAL_WEB_ORIGIN = "http://localhost:8080"

/** The origins of ALLOWED_ORIGIN, without a trailing slash. */
fun allowedOrigins(allowedOrigin: String?): List<String> =
    allowedOrigin.orEmpty().split(',').map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }

/**
 * The value of Access-Control-Allow-Origin: the request's own origin, if it is one of ours.
 * Null without an Origin (the native apps) or for an origin that isn't ours.
 */
fun corsOrigin(origin: String?, allowedOrigin: String?): String? {
    if (origin.isNullOrEmpty()) return null
    val normalized = origin.trimEnd('/')
    return normalized.takeIf { it == LOCAL_WEB_ORIGIN || it in allowedOrigins(allowedOrigin) }
}

package org.example.slippy.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.example.slippy.foundation.Call
import org.example.slippy.foundation.ERROR_CODE_ENVIRONMENT
import org.example.slippy.foundation.ERROR_CODE_HUMAN_CHECK
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.allowedOrigins
import org.example.slippy.foundation.callProvider
import web.url.URL

// The Turnstile check before a new guest (BLUE_PRINT_GUEST.md, section 2).

private const val SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify"

/** Turnstile tokens are at most 2048 characters. */
const val HUMAN_TOKEN_MAX_LENGTH = 2048

@Serializable
private class SiteverifyRequest(val secret: String, val response: String, val remoteip: String)

@Serializable
private class SiteverifyResponse(
    val success: Boolean = false,
    val hostname: String = "",
    @SerialName("error-codes") val errorCodes: List<String> = emptyList(),
)

/** wrangler dev: localhost, or a private address (the Android emulator calls 10.0.2.2). */
fun isLocalHost(host: String): Boolean {
    val h = host.lowercase().removePrefix("[").removeSuffix("]")
    if (h == "localhost" || h.endsWith(".localhost") || h == "::1" || h == "0.0.0.0") return true
    val octets = h.split('.').map { it.toIntOrNull() ?: return false }
    if (octets.size != 4) return false
    return octets[0] == 127 || octets[0] == 10 ||
        (octets[0] == 192 && octets[1] == 168) ||
        (octets[0] == 172 && octets[1] in 16..31)
}

/** The host of an origin such as "https://app.example.com:8443". */
fun hostOfOrigin(origin: String): String = origin.substringAfter("://").substringBefore('/').substringBefore(':')

/**
 * The hostname Turnstile reports must be one of ours: a host of the web app or the worker's
 * own host. Locally anything goes, because the test keys answer "example.com".
 */
fun isHumanCheckHostnameAccepted(hostname: String, workerHost: String, allowedOrigin: String?, local: Boolean): Boolean =
    local || hostname.equals(workerHost, ignoreCase = true) ||
        allowedOrigins(allowedOrigin).any { hostOfOrigin(it).equals(hostname, ignoreCase = true) }

suspend fun requireHuman(call: Call, token: String) {
    val workerHost = URL(call.request.url).hostname
    val local = isLocalHost(workerHost)
    val secret = call.env.TURNSTILE_SECRET_KEY
    if (secret.isNullOrBlank()) {
        if (local) return
        console.error("Missing secret TURNSTILE_SECRET_KEY")
        throw ServiceException(ERROR_CODE_ENVIRONMENT, "Service unavailable", 503)
    }
    if (token.isEmpty() || token.length > HUMAN_TOKEN_MAX_LENGTH) throw humanCheckFailed("no token")
    if (token.startsWith("error:")) throw humanCheckFailed("Turnstile failed on the device: ${token.take(64)}")
    val result = callProvider<SiteverifyRequest, SiteverifyResponse>(
        "Turnstile", SITEVERIFY_URL, SiteverifyRequest(secret, token, call.clientIp), emptyMap())
    if (!result.success) throw humanCheckFailed("refused: ${result.errorCodes}")
    if (!isHumanCheckHostnameAccepted(result.hostname, workerHost, call.env.ALLOWED_ORIGIN, local)) {
        throw humanCheckFailed("solved on another host: ${result.hostname}")
    }
}

private fun humanCheckFailed(reason: String): ServiceException {
    console.warn("Human check failed: $reason")
    return ServiceException(ERROR_CODE_HUMAN_CHECK, "Human check failed", 403)
}

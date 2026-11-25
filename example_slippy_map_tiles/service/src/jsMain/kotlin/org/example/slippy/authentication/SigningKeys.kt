package org.example.slippy.authentication

import org.example.slippy.foundation.ERROR_CODE_AUTHENTICATION
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.nowMillis
import web.http.fetch
import web.http.text

// Google's public keys of Firebase ID tokens, kept per isolate.

private const val JWKS_URL =
    "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"

/** Without a max-age, the keys are kept this long. */
const val DEFAULT_KEYS_MAX_AGE_SECONDS = 3600

/** An unknown kid refetches the keys at most this often. */
const val UNKNOWN_KID_REFETCH_MILLIS = 60_000.0

private var jwks: Map<String, dynamic> = emptyMap()
private var jwksExpiresAt = 0.0
private var lastUnknownKidFetch = Double.NEGATIVE_INFINITY
private val importedKeys = mutableMapOf<String, SigningKey>()

/** The max-age of a Cache-Control header, in seconds; null without one. */
fun maxAgeSeconds(cacheControl: String?): Int? =
    cacheControl?.split(',')?.map { it.trim() }
        ?.firstOrNull { it.startsWith("max-age=", ignoreCase = true) }
        ?.substringAfter('=')?.trim()?.toIntOrNull()?.takeIf { it >= 0 }

/** An unknown kid may refetch the keys, once a minute at most: a forged kid can't flood Google. */
fun mayRefetchForUnknownKid(nowMillis: Double, lastFetchMillis: Double): Boolean =
    nowMillis - lastFetchMillis >= UNKNOWN_KID_REFETCH_MILLIS

/** The key of [kid], imported once; null for a kid Google doesn't have. */
suspend fun signingKey(kid: String): SigningKey? {
    val now = nowMillis()
    if (now >= jwksExpiresAt) fetchKeys(now)
    var jwk = jwks[kid]
    if (jwk == null && mayRefetchForUnknownKid(now, lastUnknownKidFetch)) {
        lastUnknownKidFetch = now
        fetchKeys(now)
        jwk = jwks[kid]
    }
    if (jwk == null) return null
    return importedKeys[kid] ?: importRs256Key(jwk).also { importedKeys[kid] = it }
}

private suspend fun fetchKeys(now: Double) {
    val text = try {
        val response = fetch(JWKS_URL).value
        if (!response.ok) throw IllegalStateException("status ${response.status}")
        val maxAge = maxAgeSeconds(response.headers.get("Cache-Control")) ?: DEFAULT_KEYS_MAX_AGE_SECONDS
        jwksExpiresAt = now + maxAge * 1000.0
        response.text()
    } catch (e: Throwable) {
        console.error("Google's signing keys could not be fetched: ${e.message}")
        throw ServiceException(ERROR_CODE_AUTHENTICATION, "Authentication failed", 502)
    }
    val keys: Array<dynamic> = JSON.parse<dynamic>(text).keys.unsafeCast<Array<dynamic>>()
    jwks = keys.associateBy { it.kid.unsafeCast<String>() }
    // Imported keys of kids that are gone are dropped
    importedKeys.keys.retainAll(jwks.keys)
}

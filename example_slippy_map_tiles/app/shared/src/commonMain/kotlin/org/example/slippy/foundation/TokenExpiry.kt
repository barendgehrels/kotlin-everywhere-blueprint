package org.example.slippy.foundation

import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Some margin for a slow call, and for a device clock that is a bit off. */
const val REFRESH_MARGIN_SECONDS = 5 * 60L

private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

/**
 * The `exp` of a JWT, in seconds; null when it cannot be read. Only used to know when to refresh:
 * the service verifies the signature and the expiry of every token.
 */
fun tokenExpiry(idToken: String): Long? = runCatching {
    val payload = base64Url.decode(idToken.split('.')[1]).decodeToString()
    Json.parseToJsonElement(payload).jsonObject["exp"]?.jsonPrimitive?.longOrNull
}.getOrNull()

/** Whether the token must be refreshed before a call. Unreadable: no, the service decides. */
fun tokenExpiresSoon(idToken: String, nowEpochSeconds: Long): Boolean {
    val expiry = tokenExpiry(idToken) ?: return false
    return nowEpochSeconds + REFRESH_MARGIN_SECONDS >= expiry
}

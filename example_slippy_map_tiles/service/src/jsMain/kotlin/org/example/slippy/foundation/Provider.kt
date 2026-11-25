package org.example.slippy.foundation

import kotlinx.serialization.SerializationException
import web.http.BodyInit
import web.http.RequestInit
import web.http.RequestMethod
import web.http.POST
import web.http.fetch
import web.http.text

/**
 * POSTs JSON to a provider (AI or other API) and decodes its answer. On an error it logs the
 * provider's text and throws a 502: that text never reaches the user. [name] is what the log
 * says; [url] is never logged, as it may hold a key.
 */
suspend inline fun <reified Req, reified Res> callProvider(
    name: String, url: String, request: Req, headers: Map<String, String>, debug: Boolean = false,
): Res {
    val text = fetchProvider(name, url, serviceJson.encodeToString(request), headers, debug)
    return try {
        serviceJson.decodeFromString<Res>(text)
    } catch (e: SerializationException) {
        console.error("$name answered something unexpected: ${e.message}")
        throw ServiceException(ERROR_CODE_PROVIDER, "$name failed", 502)
    } catch (e: IllegalArgumentException) {
        console.error("$name answered something unexpected: ${e.message}")
        throw ServiceException(ERROR_CODE_PROVIDER, "$name failed", 502)
    }
}

/** The shared part: fetch, error handling, the debug log. Returns the answer's text. */
suspend fun fetchProvider(name: String, url: String, body: String, headers: Map<String, String>, debug: Boolean): String {
    if (debug) console.log("$name request: $body")
    val requestHeaders = headersOf("Content-Type" to "application/json")
    headers.forEach { (key, value) -> requestHeaders.set(key, value) }
    val response = try {
        fetch(url, RequestInit(method = RequestMethod.POST, headers = requestHeaders, body = BodyInit(body))).value
    } catch (e: Throwable) {
        console.error("$name could not be reached")
        throw ServiceException(ERROR_CODE_PROVIDER, "$name failed", 502)
    }
    val text = response.text()
    if (debug) console.log("$name response (${response.status}): $text")
    if (!response.ok) {
        console.error("$name failed with ${response.status}: $text")
        throw ServiceException(ERROR_CODE_PROVIDER, "$name failed", 502)
    }
    return text
}

package org.example.slippy.foundation

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import web.http.BodyInit
import web.http.Headers
import web.http.Request
import web.http.Response
import web.http.ResponseInit
import web.http.text

/** Lenient about extra fields: an older or newer peer still works. */
val serviceJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true    // Firebase needs returnSecureToken, which has a default
}

/** A request body larger than this is refused before it is decoded. */
const val MAX_BODY_LENGTH = 10_000

fun headersOf(vararg pairs: Pair<String, String>): Headers =
    Headers().also { headers -> pairs.forEach { (name, value) -> headers.set(name, value) } }

inline fun <reified T> jsonResponse(body: T, status: Int = 200): Response =
    textResponse(serviceJson.encodeToString(body), "application/json", status)

fun textResponse(text: String, contentType: String, status: Int = 200, cacheControl: String? = null): Response {
    val headers = headersOf("Content-Type" to contentType)
    if (cacheControl != null) headers.set("Cache-Control", cacheControl)
    return Response(BodyInit(text), ResponseInit(status = status.toShort(), headers = headers))
}

fun htmlResponse(html: String): Response =
    textResponse(html, "text/html; charset=utf-8", cacheControl = "no-store")

/** The body as T; invalid JSON, or a body that is too large, is a 400. */
suspend inline fun <reified T> Request.decodeBody(): T {
    val text = text()
    requireMaxLength(text, MAX_BODY_LENGTH)
    return try {
        serviceJson.decodeFromString<T>(text)
    } catch (e: SerializationException) {
        throw ServiceException(ERROR_CODE_GENERAL, "Invalid request body", 400)
    } catch (e: IllegalArgumentException) {
        throw ServiceException(ERROR_CODE_GENERAL, "Invalid request body", 400)
    }
}

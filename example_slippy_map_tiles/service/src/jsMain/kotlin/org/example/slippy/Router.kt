package org.example.slippy

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.example.slippy.account.handleChangePassword
import org.example.slippy.account.handleConfirmPage
import org.example.slippy.account.handleLogin
import org.example.slippy.account.handlePasswordReset
import org.example.slippy.account.handleRefreshToken
import org.example.slippy.account.handleRegister
import org.example.slippy.account.handleVerifyEmail
import org.example.slippy.authentication.handleHumanCheckPage
import org.example.slippy.cloudflare.ExecutionContext
import org.example.slippy.foundation.Call
import org.example.slippy.foundation.ERROR_CODE_GENERAL
import org.example.slippy.foundation.Env
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.bearerToken
import org.example.slippy.foundation.corsOrigin
import org.example.slippy.foundation.createError
import org.example.slippy.foundation.headersOf
import org.example.slippy.foundation.textResponse
import org.example.slippy.geocode.handleGeocode
import org.example.slippy.guest.handleGuest
import org.example.slippy.tile.handleTile
import web.http.Request
import web.http.Response
import web.url.URL
import kotlin.js.Promise

/**
 * The one function the runtime calls: index.js reaches it as org.example.slippy.handleRequest,
 * the package path, so it lives in the root package and not in foundation.
 */
@OptIn(ExperimentalJsExport::class, DelicateCoroutinesApi::class)
@JsExport
fun handleRequest(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> =
    // Unconfined, always: the runtime cancels continuations scheduled across requests
    GlobalScope.promise(Dispatchers.Unconfined) {
        val origin = request.headers.get("Origin")
        val corsOrigin = corsOrigin(origin, env.ALLOWED_ORIGIN)
        if (origin != null && corsOrigin == null) console.warn("CORS: origin $origin is not in ALLOWED_ORIGIN")
        val method = request.method.toString()
        val response = if (method == "OPTIONS") {
            preflightResponse()
        } else {
            val path = URL(request.url).pathname.trimEnd('/').ifEmpty { "/" }
            val call = Call(request, env, ctx, bearerToken(request.headers.get("Authorization")))
            try {
                route(method, path, call)
            } catch (e: ServiceException) {
                createError(e.code, e.message ?: "", e.status)
            } catch (e: Throwable) {
                console.error("Unexpected failure on $path: ${e.message}")
                createError(ERROR_CODE_GENERAL, "Internal error", 500)
            }
        }
        // CORS, once, here
        if (corsOrigin != null) {
            response.headers.set("Access-Control-Allow-Origin", corsOrigin)
            response.headers.append("Vary", "Origin")
        }
        response
    }

private suspend fun route(method: String, path: String, call: Call): Response = when (method) {
    "GET" -> when {
        path == "/" -> textResponse("Slippy service", "text/plain; charset=utf-8")
        path == "/confirm" -> handleConfirmPage(call)
        path == "/human_check" -> handleHumanCheckPage(call)
        path.startsWith("/tile/") -> handleTile(call, path)
        else -> notFound()
    }
    "POST" -> when (path) {
        "/login" -> handleLogin(call)
        "/register" -> handleRegister(call)
        "/refresh_token" -> handleRefreshToken(call)
        "/verify_email" -> handleVerifyEmail(call)
        "/password_reset" -> handlePasswordReset(call)
        "/change_password" -> handleChangePassword(call)
        "/guest" -> handleGuest(call)
        "/geocode" -> handleGeocode(call)
        else -> notFound()
    }
    else -> throw ServiceException(ERROR_CODE_GENERAL, "Method not allowed", 405)
}

private fun notFound(): Nothing = throw ServiceException(ERROR_CODE_GENERAL, "Not found", 404)

private fun preflightResponse(): Response =
    Response(null, web.http.ResponseInit(status = 204.toShort(), headers = headersOf(
        "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
        "Access-Control-Allow-Headers" to "Authorization, Content-Type",
        "Access-Control-Max-Age" to "86400",
    )))

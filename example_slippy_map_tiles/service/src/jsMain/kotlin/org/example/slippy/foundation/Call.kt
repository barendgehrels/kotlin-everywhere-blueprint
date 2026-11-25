package org.example.slippy.foundation

import org.example.slippy.authentication.JwtPayload
import org.example.slippy.cloudflare.ExecutionContext
import web.http.Request

/** Everything a handler gets. Adding something later changes Call, not every handler. */
class Call(val request: Request, val env: Env, val ctx: ExecutionContext, val token: String?) {
    /** The verified token and the user, once per call, filled by the guards. */
    internal var session: JwtPayload? = null
    internal var user: User? = null

    /** The client's address, set by Cloudflare: the key of the limiters per IP. */
    val clientIp: String get() = request.headers.get("CF-Connecting-IP") ?: "unknown"
}

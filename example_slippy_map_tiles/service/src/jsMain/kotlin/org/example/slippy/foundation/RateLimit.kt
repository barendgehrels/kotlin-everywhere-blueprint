package org.example.slippy.foundation

import kotlinx.coroutines.await
import org.example.slippy.cloudflare.RateLimiter
import org.example.slippy.cloudflare.rateLimitOptions

/** Over the limit is a 429; a missing binding is a 503, not a pass. */
suspend fun requireWithinLimit(limiter: RateLimiter?, name: String, key: String) {
    if (limiter == null) {
        console.error("Missing binding $name")
        throw ServiceException(ERROR_CODE_ENVIRONMENT, "Service unavailable", 503)
    }
    val outcome = limiter.limit(rateLimitOptions(key)).await()
    if (!outcome.success) throw ServiceException(ERROR_CODE_TOO_MANY_REQUESTS, "Too many requests", 429)
}

/** A limiter per IP address (new guests, login, account mails). */
suspend fun requireWithinIpLimit(call: Call, limiter: RateLimiter?, name: String) =
    requireWithinLimit(limiter, name, call.clientIp)

package org.example.slippy.cloudflare

import kotlin.js.Promise

// A [[ratelimits]] binding: https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/

external interface RateLimiter {
    fun limit(options: RateLimitOptions): Promise<RateLimitOutcome>
}

external interface RateLimitOptions {
    var key: String
}

external interface RateLimitOutcome {
    val success: Boolean
}

fun rateLimitOptions(key: String): RateLimitOptions =
    js("({})").unsafeCast<RateLimitOptions>().also { it.key = key }

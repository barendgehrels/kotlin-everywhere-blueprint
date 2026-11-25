package org.example.slippy.cloudflare

import kotlin.js.Promise

/** The third argument of the worker's fetch: work that continues after the response. */
external interface ExecutionContext {
    fun waitUntil(promise: Promise<*>)
}

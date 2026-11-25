package org.example.slippy.foundation

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

/** Bookkeeping the user doesn't wait for. A failure is logged, not returned. */
@OptIn(DelicateCoroutinesApi::class)
fun afterResponse(call: Call, what: String, block: suspend () -> Unit) {
    call.ctx.waitUntil(GlobalScope.promise(Dispatchers.Unconfined) {
        try {
            block()
        } catch (e: Throwable) {
            console.error("$what failed: ${e.message}")
        }
    })
}

package org.example.slippy.foundation

import androidx.compose.runtime.Composable
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay

// In index.html: loads Turnstile the first time, and renders the widget below the app
private external fun requestHumanCheck()

// In index.html: the token, "" without a site key or when Turnstile failed, null while there is none
private external fun humanCheckResult(): String?

private val POLL_INTERVAL = 200.milliseconds
private val MAX_WAIT = 3.minutes

actual suspend fun humanCheckToken(): String? {
    requestHumanCheck()
    repeat((MAX_WAIT / POLL_INTERVAL).toInt()) {
        humanCheckResult()?.let { return it }
        delay(POLL_INTERVAL)
    }
    return ""
}

actual val hasHumanCheck: Boolean = true

// The page shows the widget itself
@Composable
actual fun HumanCheckHost() {
}

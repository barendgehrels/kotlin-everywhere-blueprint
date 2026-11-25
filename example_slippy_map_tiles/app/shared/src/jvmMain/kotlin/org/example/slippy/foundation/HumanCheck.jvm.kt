package org.example.slippy.foundation

import androidx.compose.runtime.Composable

// No check on the desktop, because a web view is heavy there: log in or register instead
actual suspend fun humanCheckToken(): String? = null

actual val hasHumanCheck: Boolean = false

@Composable
actual fun HumanCheckHost() {
}

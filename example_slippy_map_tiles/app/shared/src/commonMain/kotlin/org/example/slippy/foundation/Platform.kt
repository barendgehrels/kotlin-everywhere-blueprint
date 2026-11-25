package org.example.slippy.foundation

import androidx.compose.runtime.Composable

/** What differs per platform, apart from storage and the human check. */
interface Platform {
    fun log(message: String)

    @Composable
    fun getVersionName(): String

    /** On Android, localhost becomes 10.0.2.2, so the emulator reaches the development machine. */
    fun reachableUrl(url: String): String
}

expect fun getPlatform(): Platform

fun log(message: String) = getPlatform().log(message)

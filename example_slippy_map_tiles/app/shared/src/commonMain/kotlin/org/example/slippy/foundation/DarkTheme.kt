package org.example.slippy.foundation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/** The theme chosen at startup; null follows the system. */
val LocalDarkTheme = compositionLocalOf<Boolean?> { null }

/** All app code calls this, never isSystemInDarkTheme() directly. */
@Composable
fun isDarkTheme(): Boolean = LocalDarkTheme.current ?: isSystemInDarkTheme()

/** The startup option `theme`: "dark", "light", or anything else to follow the system. */
fun darkThemeOf(theme: String?): Boolean? = when (theme) {
    "dark" -> true
    "light" -> false
    else -> null
}

@Composable
fun SlippyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

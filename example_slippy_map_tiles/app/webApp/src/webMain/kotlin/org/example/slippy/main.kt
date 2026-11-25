package org.example.slippy

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.example.slippy.foundation.MinimalArgumentParser
import org.example.slippy.foundation.darkThemeOf
import web.window.window

/** Startup options are the URL query: `?theme=dark`. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val arguments = queryArguments(window.location.search)
    val darkTheme = darkThemeOf(MinimalArgumentParser(arguments).value("theme"))
    // The app above the human check and the footer of index.html
    ComposeViewport(viewportContainerId = "composeApplication") {
        App(darkTheme = darkTheme)
    }
}

/** The query as arguments for the same parser as the other platforms: `?theme=dark` is `-theme dark`. */
private fun queryArguments(search: String): List<String> =
    search.removePrefix("?")
        .split("&")
        .filter { it.contains('=') }
        .flatMap { pair ->
            val (key, value) = pair.split("=", limit = 2)
            listOf("-$key", value)
        }

package org.example.slippy

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.example.slippy.foundation.MinimalArgumentParser
import org.example.slippy.foundation.darkThemeOf

/** Startup options: `--theme dark`. */
fun main(args: Array<String>) {
    val darkTheme = darkThemeOf(MinimalArgumentParser(args.toList()).value("theme"))
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Slippy",
        ) {
            App(darkTheme = darkTheme)
        }
    }
}

package org.example.slippy

import androidx.compose.ui.window.ComposeUIViewController
import org.example.slippy.foundation.MinimalArgumentParser
import org.example.slippy.foundation.darkThemeOf
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController

/** Startup options are launch arguments, such as `-theme dark`. */
fun MainViewController(): UIViewController {
    val arguments = NSProcessInfo.processInfo.arguments.map { it.toString() }
    val darkTheme = darkThemeOf(MinimalArgumentParser(arguments).value("theme"))
    return ComposeUIViewController { App(darkTheme = darkTheme) }
}

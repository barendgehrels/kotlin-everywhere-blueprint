package org.example.slippy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.example.slippy.foundation.darkThemeOf
import org.example.slippy.foundation.initAndroidPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAndroidPlatform(this)

        // Startup options are intent extras: adb shell am start ... --es theme dark
        val darkTheme = darkThemeOf(intent.getStringExtra("theme"))
        setContent {
            App(darkTheme = darkTheme)
        }
    }
}

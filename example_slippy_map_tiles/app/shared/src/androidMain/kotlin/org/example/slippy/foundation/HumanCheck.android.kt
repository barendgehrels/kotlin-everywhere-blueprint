package org.example.slippy.foundation

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

actual suspend fun humanCheckToken(): String? = HumanCheckRequests.token()

actual val hasHumanCheck: Boolean = true

@Composable
actual fun HumanCheckHost() {
    HumanCheckDialog { modifier, url, onToken, onHeight ->
        HumanCheckWebView(modifier, url, onToken, onHeight)
    }
}

/** The page's bridge: `window.HumanCheck.onToken(token)` and `onHeight(cssPixels)`. */
private class HumanCheckBridge(val onToken: (String) -> Unit, val onHeight: (Int) -> Unit) {
    // JavaScript calls on a background thread: hand over to the main thread
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onToken(token: String) {
        main.post { onToken.invoke(token) }
    }

    @JavascriptInterface
    fun onHeight(height: Double) {
        main.post { onHeight.invoke(height.toInt()) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HumanCheckWebView(
    modifier: androidx.compose.ui.Modifier,
    url: String,
    onToken: (String) -> Unit,
    onHeight: (Int) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                WebView.setWebContentsDebuggingEnabled(true)     // chrome://inspect
            }
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(Color.TRANSPARENT)
                addJavascriptInterface(HumanCheckBridge(onToken, onHeight), "HumanCheck")
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
    )
}

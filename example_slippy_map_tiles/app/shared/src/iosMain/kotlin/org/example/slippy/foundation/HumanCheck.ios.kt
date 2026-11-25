package org.example.slippy.foundation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIColor
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

actual suspend fun humanCheckToken(): String? = HumanCheckRequests.token()

actual val hasHumanCheck: Boolean = true

@Composable
actual fun HumanCheckHost() {
    HumanCheckDialog { modifier, url, onToken, onHeight ->
        HumanCheckWebView(modifier, url, onToken, onHeight)
    }
}

private const val TOKEN_HANDLER = "humanCheck"
private const val HEIGHT_HANDLER = "humanCheckHeight"

/** The page's bridge: `window.webkit.messageHandlers.humanCheck.postMessage(token)`, and the height. */
private class HumanCheckMessageHandler(
    val onToken: (String) -> Unit,
    val onHeight: (Int) -> Unit,
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val body = didReceiveScriptMessage.body
        when (didReceiveScriptMessage.name) {
            TOKEN_HANDLER -> onToken(body as? String ?: "")
            HEIGHT_HANDLER -> (body as? NSNumber)?.let { onHeight(it.doubleValue.toInt()) }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun HumanCheckWebView(modifier: Modifier, url: String, onToken: (String) -> Unit, onHeight: (Int) -> Unit) {
    UIKitView(
        modifier = modifier,
        factory = {
            val handler = HumanCheckMessageHandler(onToken, onHeight)
            val configuration = WKWebViewConfiguration()
            configuration.userContentController.addScriptMessageHandler(handler, TOKEN_HANDLER)
            configuration.userContentController.addScriptMessageHandler(handler, HEIGHT_HANDLER)
            WKWebView(frame = CGRectZero.readValue(), configuration = configuration).apply {
                setOpaque(false)
                backgroundColor = UIColor.clearColor
                scrollView.scrollEnabled = false
                NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest.requestWithURL(it)) }
            }
        },
        // The content controller holds on to the handlers: without this the web view is never freed
        onRelease = { webView ->
            webView.configuration.userContentController.removeScriptMessageHandlerForName(TOKEN_HANDLER)
            webView.configuration.userContentController.removeScriptMessageHandlerForName(HEIGHT_HANDLER)
        },
    )
}

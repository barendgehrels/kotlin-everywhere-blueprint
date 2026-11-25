package org.example.slippy.authentication

import org.example.slippy.foundation.Call
import org.example.slippy.foundation.ERROR_CODE_ENVIRONMENT
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.htmlResponse
import web.http.Response

// GET /human_check: Turnstile for the native apps, in a web view on the worker's own host
// (BLUE_PRINT_GUEST.md, 3.2). The token goes back through the app's bridge.

private val siteKeyRegex = Regex("^[A-Za-z0-9_-]*$")

/** The site key goes into a script, so it must be a plain key. */
fun isSiteKeySafe(siteKey: String): Boolean = siteKeyRegex.matches(siteKey)

fun handleHumanCheckPage(call: Call): Response {
    val siteKey = call.env.TURNSTILE_SITE_KEY.orEmpty().trim()
    if (!isSiteKeySafe(siteKey)) {
        console.error("TURNSTILE_SITE_KEY has characters a site key never has")
        throw ServiceException(ERROR_CODE_ENVIRONMENT, "Configuration error", 500)
    }
    return htmlResponse(humanCheckPage(siteKey))
}

private fun humanCheckPage(siteKey: String): String = """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  html, body { margin: 0; padding: 0; background: transparent; }
  #human-check { display: flex; justify-content: center; }
</style>
</head>
<body>
<div id="human-check"></div>
<script>
  // Android: window.HumanCheck (a @JavascriptInterface); iOS: WKScriptMessageHandlers
  function sendToken(token) {
    if (window.HumanCheck && window.HumanCheck.onToken) {
      window.HumanCheck.onToken(token);
    } else if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.humanCheck) {
      window.webkit.messageHandlers.humanCheck.postMessage(token);
    }
  }
  function sendHeight(height) {
    if (window.HumanCheck && window.HumanCheck.onHeight) {
      window.HumanCheck.onHeight(height);
    } else if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.humanCheckHeight) {
      window.webkit.messageHandlers.humanCheckHeight.postMessage(height);
    }
  }
  var siteKey = "$siteKey";
  function onTurnstileLoad() {
    turnstile.render("#human-check", {
      sitekey: siteKey,
      theme: "auto",
      callback: function (token) { sendToken(token); },
      "error-callback": function (code) { sendToken("error:" + code); return true; },
      "expired-callback": function () { sendToken("error:expired"); }
    });
  }
  if (!siteKey) {
    // Local development without a site key: the service skips the check
    sendToken("");
  } else {
    new ResizeObserver(function (entries) {
      sendHeight(Math.ceil(entries[0].contentRect.height));
    }).observe(document.getElementById("human-check"));
    var script = document.createElement("script");
    script.src = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&onload=onTurnstileLoad";
    script.async = true;
    document.head.appendChild(script);
  }
</script>
</body>
</html>
""".trimIndent()

package org.example.slippy.account

import org.example.slippy.foundation.Call
import org.example.slippy.foundation.htmlResponse
import web.http.Response

/** GET /confirm: where the verification email's link ends. */
@Suppress("UNUSED_PARAMETER")
fun handleConfirmPage(call: Call): Response = htmlResponse("""
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Slippy</title>
<style>
  :root { color-scheme: light dark; }
  body { font-family: system-ui, sans-serif; margin: 0; padding: 48px 16px; text-align: center; }
</style>
</head>
<body>
<h1>Slippy</h1>
<p>Your email address is verified. Go back to the app.</p>
</body>
</html>
""".trimIndent())

package org.example.slippy.foundation

/** The token of "Authorization: Bearer <token>", or null. */
fun bearerToken(authorization: String?): String? =
    authorization?.trim()?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substring(7)?.trim()?.takeIf { it.isNotEmpty() }

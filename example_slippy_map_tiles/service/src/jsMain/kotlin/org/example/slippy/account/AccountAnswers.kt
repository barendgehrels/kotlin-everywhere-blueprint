package org.example.slippy.account

import org.example.slippy.authentication.FirebaseTokens
import org.example.slippy.authentication.JwtPayload

/** The answer of every account call that gives tokens. */
fun accountResponse(tokens: FirebaseTokens, payload: JwtPayload, isGuest: Boolean = false, name: String? = null) =
    AccountResponse(
        idToken = tokens.idToken,
        refreshToken = tokens.refreshToken,
        emailVerified = payload.emailVerified,
        isGuest = isGuest,
        name = name,
    )

/** The answer of the calls without one (the emails): an empty object. */
@kotlinx.serialization.Serializable
class EmptyResponse

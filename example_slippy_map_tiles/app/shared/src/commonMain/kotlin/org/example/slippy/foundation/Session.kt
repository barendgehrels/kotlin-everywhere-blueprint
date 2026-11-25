package org.example.slippy.foundation

import kotlinx.serialization.Serializable
import org.example.slippy.account.AccountResponse

/** The session as the app keeps it, in local storage too. All null: logged out. */
@Serializable
data class Session(
    val idToken: String? = null,
    val refreshToken: String? = null,
    val emailVerified: Boolean = false,
    val isGuest: Boolean = false,
    val name: String? = null,          // a guest's name
    val email: String? = null,         // a registered user's address, as typed at login
) {
    val hasSession: Boolean get() = idToken != null
    val isRegistered: Boolean get() = hasSession && !isGuest
}

fun AccountResponse.toSession(email: String?) = Session(
    idToken = idToken,
    refreshToken = refreshToken,
    emailVerified = emailVerified,
    isGuest = isGuest,
    name = name,
    email = if (isGuest) null else email,
)

/** A guest who logged out, remembered on this device to resume it by name. */
@Serializable
data class KnownGuest(val name: String, val refreshToken: String)

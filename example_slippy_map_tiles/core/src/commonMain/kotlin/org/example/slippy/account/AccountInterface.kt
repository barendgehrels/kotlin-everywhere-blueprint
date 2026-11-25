package org.example.slippy.account

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

/** With a guest's bearer token, the guest becomes this account (same Firebase uid). */
@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class PasswordResetRequest(val email: String)

@Serializable
data class ChangePasswordRequest(val idToken: String, val password: String)

@Serializable
data class GuestRequest(val humanToken: String, val name: String)

@Serializable
data class AccountResponse(
    val idToken: String,
    val refreshToken: String,
    val emailVerified: Boolean,
    val isGuest: Boolean = false,
    val name: String? = null,            // a guest's name
)

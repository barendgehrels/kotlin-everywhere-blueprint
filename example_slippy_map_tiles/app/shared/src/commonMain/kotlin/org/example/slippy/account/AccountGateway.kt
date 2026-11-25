package org.example.slippy.account

import org.example.slippy.foundation.NetworkClient
import org.example.slippy.foundation.Session
import org.example.slippy.foundation.SessionStore
import org.example.slippy.foundation.toSession

interface AccountGateway {
    suspend fun logIn(email: String, password: String): Result<Session>

    /** A new account; a guest's session becomes this account (the same user). */
    suspend fun register(email: String, password: String): Result<Session>

    suspend fun sendVerificationEmail(): Result<Unit>

    suspend fun resetPassword(email: String): Result<Unit>

    /** New tokens: the old ones stop working. */
    suspend fun changePassword(password: String): Result<Session>
}

class CloudAccountGateway(private val session: SessionStore) : AccountGateway {
    override suspend fun logIn(email: String, password: String): Result<Session> =
        NetworkClient.postJson<LoginRequest, AccountResponse>("login", LoginRequest(email, password))
            .map { it.toSession(email) }

    override suspend fun register(email: String, password: String): Result<Session> {
        // A guest's ID token as the bearer, so the service links the email to that user
        val guestToken = if (session.session.value.isGuest) session.validIdToken() else null
        return NetworkClient.postJson<RegisterRequest, AccountResponse>(
            "register", RegisterRequest(email, password), idToken = guestToken
        ).map { it.toSession(email) }
    }

    override suspend fun sendVerificationEmail(): Result<Unit> = session.withIdToken { idToken ->
        NetworkClient.postJson<Unit, Unit>("verify_email", idToken = idToken)
    }

    override suspend fun resetPassword(email: String): Result<Unit> =
        NetworkClient.postJson<PasswordResetRequest, Unit>("password_reset", PasswordResetRequest(email))

    override suspend fun changePassword(password: String): Result<Session> {
        val email = session.session.value.email
        return session.withIdToken { idToken ->
            NetworkClient.postJson<ChangePasswordRequest, AccountResponse>(
                "change_password", ChangePasswordRequest(idToken, password), idToken = idToken
            ).map { it.toSession(email) }
        }
    }
}

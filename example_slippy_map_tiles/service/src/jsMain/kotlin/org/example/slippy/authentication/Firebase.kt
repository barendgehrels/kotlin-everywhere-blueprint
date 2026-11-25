package org.example.slippy.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.example.slippy.foundation.ENV_FIREBASE_API_KEY
import org.example.slippy.foundation.ERROR_CODE_AUTHENTICATION
import org.example.slippy.foundation.ERROR_CODE_TOO_MANY_REQUESTS
import org.example.slippy.foundation.Env
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.headersOf
import org.example.slippy.foundation.requireEnv
import org.example.slippy.foundation.serviceJson
import web.http.BodyInit
import web.http.POST
import web.http.RequestInit
import web.http.RequestMethod
import web.http.fetch
import web.http.text

// Firebase Authentication through its REST API (the Identity Toolkit and securetoken).
// Only the worker calls it; the apps need no Firebase SDK.

private const val IDENTITY_TOOLKIT = "https://identitytoolkit.googleapis.com/v1/accounts:"
private const val SECURE_TOKEN = "https://securetoken.googleapis.com/v1/token"

/** A Firebase error that the call expects: the user's problem, with its own code. */
class ExpectedError(val code: Int, val status: Int = 400)

@Serializable
class FirebaseTokens(val idToken: String, val refreshToken: String, val localId: String = "")

@Serializable
private class SecureTokens(
    @SerialName("id_token") val idToken: String,
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
private class PasswordRequest(val email: String, val password: String, val returnSecureToken: Boolean = true)

@Serializable
private class AnonymousSignUpRequest(val returnSecureToken: Boolean = true)

@Serializable
private class UpdateRequest(
    val idToken: String, val email: String? = null, val password: String? = null,
    val returnSecureToken: Boolean = true,
)

@Serializable
private class OobRequest(
    val requestType: String, val idToken: String? = null, val email: String? = null,
    val continueUrl: String? = null,
)

@Serializable
private class FirebaseErrorBody(val error: FirebaseError? = null)

@Serializable
private class FirebaseError(val message: String = "")

@Serializable
private class Empty

/** "WEAK_PASSWORD : Password should be..." → "WEAK_PASSWORD". */
fun firebaseErrorName(message: String): String = message.substringBefore(':').trim().substringBefore(' ')

/**
 * The exception for a failed Firebase call: an expected 4xx is the user's problem, too many
 * attempts is a 429, and anything else is Firebase's problem, a 502.
 */
fun authenticationFailure(status: Int, firebaseMessage: String, expected: Map<String, ExpectedError>): ServiceException {
    val name = firebaseErrorName(firebaseMessage)
    val known = expected[name]
    return when {
        status == 429 || name.startsWith("TOO_MANY_ATTEMPTS") ->
            ServiceException(ERROR_CODE_TOO_MANY_REQUESTS, "Too many attempts, try again later", 429)
        status in 400..499 && known != null -> ServiceException(known.code, name, known.status)
        else -> ServiceException(ERROR_CODE_AUTHENTICATION, "Authentication failed", 502)
    }
}

/** POSTs to Firebase and returns the answer's text; failures become a ServiceException. */
suspend fun callAuthentication(
    url: String, body: String, contentType: String, expected: Map<String, ExpectedError>,
): String {
    val response = try {
        fetch(url, RequestInit(
            method = RequestMethod.POST,
            headers = headersOf("Content-Type" to contentType),
            body = BodyInit(body),
        )).value
    } catch (e: Throwable) {
        console.error("Firebase could not be reached")
        throw ServiceException(ERROR_CODE_AUTHENTICATION, "Authentication failed", 502)
    }
    val text = response.text()
    if (response.ok) return text
    val status = response.status.toInt()
    // Firebase's message is a code such as EMAIL_EXISTS: safe to log, never the user's input
    val message = try {
        serviceJson.decodeFromString<FirebaseErrorBody>(text).error?.message.orEmpty()
    } catch (e: Exception) {
        ""
    }
    val failure = authenticationFailure(status, message, expected)
    if (failure.status >= 500) console.error("Firebase failed with $status: $message")
    else console.warn("Firebase refused with $status: $message")
    throw failure
}

private suspend inline fun <reified Req, reified Res> callIdentityToolkit(
    env: Env, method: String, request: Req, expected: Map<String, ExpectedError>,
): Res {
    val key = requireEnv(env.FIREBASE_API_KEY, ENV_FIREBASE_API_KEY)
    val text = callAuthentication("$IDENTITY_TOOLKIT$method?key=$key",
        serviceJson.encodeToString(request), "application/json", expected)
    return try {
        serviceJson.decodeFromString<Res>(text)
    } catch (e: Exception) {
        console.error("Firebase $method answered something unexpected")
        throw ServiceException(ERROR_CODE_AUTHENTICATION, "Authentication failed", 502)
    }
}

suspend fun signInWithPassword(env: Env, email: String, password: String, expected: Map<String, ExpectedError>) =
    callIdentityToolkit<_, FirebaseTokens>(env, "signInWithPassword", PasswordRequest(email, password), expected)

suspend fun signUpWithPassword(env: Env, email: String, password: String, expected: Map<String, ExpectedError>) =
    callIdentityToolkit<_, FirebaseTokens>(env, "signUp", PasswordRequest(email, password), expected)

/** A new anonymous user: a guest. Needs the Anonymous provider in Firebase. */
suspend fun signUpAnonymously(env: Env) =
    callIdentityToolkit<_, FirebaseTokens>(env, "signUp", AnonymousSignUpRequest(), emptyMap())

/** Changes the user of [idToken]: links an email and password to a guest, or sets a new password. */
suspend fun updateAccount(
    env: Env, idToken: String, email: String?, password: String?, expected: Map<String, ExpectedError>,
) = callIdentityToolkit<_, FirebaseTokens>(env, "update", UpdateRequest(idToken, email, password), expected)

/** Firebase sends an email: VERIFY_EMAIL (with idToken) or PASSWORD_RESET (with email). */
suspend fun sendOobCode(
    env: Env, requestType: String, idToken: String?, email: String?, continueUrl: String?,
    expected: Map<String, ExpectedError>,
) {
    callIdentityToolkit<_, Empty>(env, "sendOobCode", OobRequest(requestType, idToken, email, continueUrl), expected)
}

/** New tokens for a refresh token. */
suspend fun refreshTokens(env: Env, refreshToken: String, expected: Map<String, ExpectedError>): FirebaseTokens {
    val key = requireEnv(env.FIREBASE_API_KEY, ENV_FIREBASE_API_KEY)
    val body = "grant_type=refresh_token&refresh_token=${encodeURIComponent(refreshToken)}"
    val text = callAuthentication("$SECURE_TOKEN?key=$key", body, "application/x-www-form-urlencoded", expected)
    val tokens = try {
        serviceJson.decodeFromString<SecureTokens>(text)
    } catch (e: Exception) {
        console.error("Firebase token refresh answered something unexpected")
        throw ServiceException(ERROR_CODE_AUTHENTICATION, "Authentication failed", 502)
    }
    return FirebaseTokens(tokens.idToken, tokens.refreshToken)
}

private external fun encodeURIComponent(value: String): String

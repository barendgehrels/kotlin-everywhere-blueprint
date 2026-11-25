package org.example.slippy.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.example.slippy.foundation.ENV_FIREBASE_PROJECT_ID
import org.example.slippy.foundation.ERROR_CODE_TOKEN_VALIDITY
import org.example.slippy.foundation.Env
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.nowMillis
import org.example.slippy.foundation.requireEnv
import org.example.slippy.foundation.serviceJson
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** A Firebase ID token is far shorter; a longer one isn't parsed at all. */
const val ID_TOKEN_MAX_LENGTH = 4096

/** Tolerated clock difference with Google, in seconds, for iat and auth_time. */
private const val CLOCK_SKEW_SECONDS = 60L

@Serializable
data class JwtHeader(val alg: String = "", val kid: String = "")

@Serializable
data class FirebaseClaims(@SerialName("sign_in_provider") val signInProvider: String = "")

/** The claims of a Firebase ID token that the service uses. */
@Serializable
data class JwtPayload(
    val sub: String = "",
    val iss: String = "",
    val aud: String = "",
    val exp: Long = 0,
    val iat: Long = 0,
    @SerialName("auth_time") val authTime: Long? = null,
    val email: String? = null,
    @SerialName("email_verified") val emailVerified: Boolean = false,
    val firebase: FirebaseClaims? = null,
) {
    /** A guest: an anonymous Firebase user. */
    val isAnonymous: Boolean get() = firebase?.signInProvider == "anonymous"
}

class ParsedJwt(val header: JwtHeader, val payload: JwtPayload, val signingInput: String, val signature: ByteArray)

@OptIn(ExperimentalEncodingApi::class)
private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

/** The three parts of a JWT, decoded; null when it isn't one. The signature is not checked here. */
@OptIn(ExperimentalEncodingApi::class)
fun parseJwt(token: String): ParsedJwt? {
    if (token.length > ID_TOKEN_MAX_LENGTH) return null
    val parts = token.split('.')
    if (parts.size != 3 || parts.any { it.isEmpty() }) return null
    return try {
        val header = serviceJson.decodeFromString<JwtHeader>(base64Url.decode(parts[0]).decodeToString())
        val payload = serviceJson.decodeFromString<JwtPayload>(base64Url.decode(parts[1]).decodeToString())
        ParsedJwt(header, payload, "${parts[0]}.${parts[1]}", base64Url.decode(parts[2]))
    } catch (e: Exception) {
        null
    }
}

/** Why the token's header and claims are not those of our Firebase project, or null when they are. */
fun jwtProblem(jwt: ParsedJwt, projectId: String, nowSeconds: Long): String? {
    val payload = jwt.payload
    return when {
        jwt.header.alg != "RS256" -> "alg is not RS256"
        jwt.header.kid.isEmpty() -> "no kid"
        payload.iss != "https://securetoken.google.com/$projectId" -> "wrong iss"
        payload.aud != projectId -> "wrong aud"
        payload.exp <= nowSeconds -> "expired"
        payload.iat > nowSeconds + CLOCK_SKEW_SECONDS -> "issued in the future"
        payload.authTime != null && payload.authTime > nowSeconds + CLOCK_SKEW_SECONDS -> "auth_time in the future"
        payload.sub.isEmpty() || payload.sub.length > 128 -> "invalid sub"
        else -> null
    }
}

/** The verified claims of a Firebase ID token; a 401 (TOKEN_VALIDITY) for any token that isn't valid. */
suspend fun verifyIdToken(env: Env, token: String): JwtPayload {
    val projectId = requireEnv(env.FIREBASE_PROJECT_ID, ENV_FIREBASE_PROJECT_ID)
    val jwt = parseJwt(token) ?: throw invalidToken("not a JWT")
    jwtProblem(jwt, projectId, (nowMillis() / 1000).toLong())?.let { throw invalidToken(it) }
    val key = signingKey(jwt.header.kid) ?: throw invalidToken("unknown kid")
    if (!verifyRs256(key, jwt.signature, jwt.signingInput.encodeToByteArray())) throw invalidToken("bad signature")
    return jwt.payload
}

private fun invalidToken(reason: String): ServiceException {
    console.warn("Invalid ID token: $reason")
    return ServiceException(ERROR_CODE_TOKEN_VALIDITY, "Invalid token", 401)
}

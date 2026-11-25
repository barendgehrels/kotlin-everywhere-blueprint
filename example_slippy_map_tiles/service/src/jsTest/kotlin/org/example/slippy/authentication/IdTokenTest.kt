package org.example.slippy.authentication

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class IdTokenTest {
    private val project = "slippy-test"
    private val now = 1_800_000_000L

    private fun part(json: String) =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(json.encodeToByteArray())

    private fun token(
        alg: String = "RS256", kid: String = "k1", iss: String = "https://securetoken.google.com/$project",
        aud: String = project, exp: Long = now + 3600, iat: Long = now - 10, sub: String = "uid1",
        provider: String = "password", verified: Boolean = true,
    ): String {
        val header = """{"alg":"$alg","kid":"$kid","typ":"JWT"}"""
        val payload = """{"iss":"$iss","aud":"$aud","exp":$exp,"iat":$iat,"auth_time":$iat,"sub":"$sub",
            "email":"a@b.nl","email_verified":$verified,"firebase":{"sign_in_provider":"$provider"}}"""
        return "${part(header)}.${part(payload)}.${part("signature")}"
    }

    private fun problem(token: String) = jwtProblem(assertNotNull(parseJwt(token)), project, now)

    @Test
    fun validTokenParsesWithItsClaims() {
        val jwt = assertNotNull(parseJwt(token()))
        assertEquals("k1", jwt.header.kid)
        assertEquals("uid1", jwt.payload.sub)
        assertTrue(jwt.payload.emailVerified)
        assertFalse(jwt.payload.isAnonymous)
        assertEquals("signature", jwt.signature.decodeToString())
        assertNull(jwtProblem(jwt, project, now))
    }

    @Test
    fun anonymousProviderIsAGuest() =
        assertTrue(assertNotNull(parseJwt(token(provider = "anonymous"))).payload.isAnonymous)

    @Test
    fun notThreePartsIsNoJwt() {
        assertNull(parseJwt("a.b"))
        assertNull(parseJwt("a.b.c.d"))
        assertNull(parseJwt("..."))
    }

    @Test
    fun garbageIsNoJwt() = assertNull(parseJwt("!!!.???.###"))

    @Test
    fun overlongTokenIsNotParsed() = assertNull(parseJwt("a".repeat(ID_TOKEN_MAX_LENGTH + 1)))

    @Test
    fun otherAlgorithmIsRefused() = assertEquals("alg is not RS256", problem(token(alg = "none")))

    @Test
    fun otherProjectIsRefused() {
        assertEquals("wrong iss", problem(token(iss = "https://securetoken.google.com/other")))
        assertEquals("wrong aud", problem(token(aud = "other")))
    }

    @Test
    fun expiredTokenIsRefused() = assertEquals("expired", problem(token(exp = now)))

    @Test
    fun tokenFromTheFutureIsRefused() = assertEquals("issued in the future", problem(token(iat = now + 120)))

    @Test
    fun smallClockSkewIsAccepted() = assertNull(problem(token(iat = now + 30)))

    @Test
    fun emptySubjectIsRefused() = assertEquals("invalid sub", problem(token(sub = "")))

    @Test
    fun maxAgeIsReadFromCacheControl() {
        assertEquals(19845, maxAgeSeconds("public, max-age=19845, must-revalidate, no-transform"))
        assertNull(maxAgeSeconds("no-cache"))
        assertNull(maxAgeSeconds(null))
    }

    @Test
    fun unknownKidRefetchesAtMostOnceAMinute() {
        assertTrue(mayRefetchForUnknownKid(100_000.0, Double.NEGATIVE_INFINITY))
        assertFalse(mayRefetchForUnknownKid(100_000.0, 50_000.0))
        assertTrue(mayRefetchForUnknownKid(110_000.0, 50_000.0))
    }

    @Test
    fun firebaseErrorNameDropsTheExplanation() {
        assertEquals("WEAK_PASSWORD", firebaseErrorName("WEAK_PASSWORD : Password should be at least 6 characters"))
        assertEquals("EMAIL_EXISTS", firebaseErrorName("EMAIL_EXISTS"))
    }
}

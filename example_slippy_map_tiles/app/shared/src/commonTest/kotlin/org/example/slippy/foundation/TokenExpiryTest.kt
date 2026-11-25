package org.example.slippy.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenExpiryTest {
    private val now = 1_800_000_000L

    @Test
    fun expiryIsReadFromThePayload() {
        assertEquals(now + 3600, tokenExpiry(testToken(now + 3600)))
    }

    @Test
    fun freshTokenIsNotRefreshed() {
        assertFalse(tokenExpiresSoon(testToken(now + 3600), now))
    }

    @Test
    fun tokenWithinTheMarginIsRefreshed() {
        assertTrue(tokenExpiresSoon(testToken(now + REFRESH_MARGIN_SECONDS - 1), now))
        assertTrue(tokenExpiresSoon(testToken(now + REFRESH_MARGIN_SECONDS), now))
        assertFalse(tokenExpiresSoon(testToken(now + REFRESH_MARGIN_SECONDS + 1), now))
    }

    @Test
    fun expiredTokenIsRefreshed() {
        assertTrue(tokenExpiresSoon(testToken(now - 10), now))
    }

    // The service decides on a token the app cannot read
    @Test
    fun unreadableTokenIsNotRefreshed() {
        assertNull(tokenExpiry("not-a-jwt"))
        assertFalse(tokenExpiresSoon("not-a-jwt", now))
        assertFalse(tokenExpiresSoon("a.%%%.c", now))
    }
}

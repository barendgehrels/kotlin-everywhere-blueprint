package org.example.slippy.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CorsTest {
    private val allowed = "https://slippy-web.acme.workers.dev, https://slippy.example.com/"

    @Test
    fun listedOriginIsEchoed() =
        assertEquals("https://slippy.example.com", corsOrigin("https://slippy.example.com", allowed))

    @Test
    fun localWebAppIsAlwaysAllowed() = assertEquals(LOCAL_WEB_ORIGIN, corsOrigin(LOCAL_WEB_ORIGIN, null))

    @Test
    fun otherOriginGetsNoHeader() = assertNull(corsOrigin("https://evil.example.org", allowed))

    @Test
    fun otherPortGetsNoHeader() = assertNull(corsOrigin("https://slippy.example.com:8443", allowed))

    @Test
    fun nativeAppsSendNoOrigin() = assertNull(corsOrigin(null, allowed))

    @Test
    fun bearerTokenIsTakenFromTheHeader() {
        assertEquals("abc", bearerToken("Bearer abc"))
        assertNull(bearerToken("Basic abc"))
        assertNull(bearerToken("Bearer "))
        assertNull(bearerToken(null))
    }
}

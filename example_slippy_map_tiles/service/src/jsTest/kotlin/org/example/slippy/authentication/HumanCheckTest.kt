package org.example.slippy.authentication

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HumanCheckTest {
    private val worker = "slippy-service-x.acme.workers.dev"
    private val origins = "https://slippy-web-y.acme.workers.dev, https://slippy.example.com/"

    @Test
    fun workerHostIsAccepted() = assertTrue(isHumanCheckHostnameAccepted(worker, worker, origins, local = false))

    @Test
    fun webAppHostsAreAccepted() {
        assertTrue(isHumanCheckHostnameAccepted("slippy-web-y.acme.workers.dev", worker, origins, local = false))
        assertTrue(isHumanCheckHostnameAccepted("slippy.example.com", worker, origins, local = false))
    }

    @Test
    fun someoneElsesPageIsRefused() =
        assertFalse(isHumanCheckHostnameAccepted("evil.example.org", worker, origins, local = false))

    @Test
    fun locallyTheTestKeysHostIsAccepted() =
        assertTrue(isHumanCheckHostnameAccepted("example.com", "localhost", origins, local = true))

    @Test
    fun localAndPrivateAddressesAreLocal() {
        listOf("localhost", "127.0.0.1", "10.0.2.2", "192.168.1.20", "172.20.0.1", "[::1]").forEach {
            assertTrue(isLocalHost(it), it)
        }
    }

    @Test
    fun publicHostsAreNotLocal() {
        listOf(worker, "172.32.0.1", "8.8.8.8", "localhost.evil.com").forEach { assertFalse(isLocalHost(it), it) }
    }

    @Test
    fun siteKeyWithScriptCharactersIsRefused() {
        assertTrue(isSiteKeySafe("1x00000000000000000000AA"))
        assertTrue(isSiteKeySafe(""))
        assertFalse(isSiteKeySafe("abc\";alert(1);//"))
    }
}

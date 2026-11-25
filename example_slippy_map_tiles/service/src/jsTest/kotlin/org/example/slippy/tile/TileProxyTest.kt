package org.example.slippy.tile

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileProxyTest {
    @Test
    fun requestPathGivesTheTile() = assertEquals(Tile(3, 4, 2), tileOfPath("/tile/3/4/2"))

    @Test
    fun tileOutsideItsZoomIsRefused() = assertNull(tileOfPath("/tile/1/2/0"))

    @Test
    fun otherPathsAreRefused() {
        assertNull(tileOfPath("/tile/3/4"))
        assertNull(tileOfPath("/tile/3/4/2/1"))
        assertNull(tileOfPath("/tile/3/-4/2"))
        assertNull(tileOfPath("/tile/23/0/0"))
        assertNull(tileOfPath("/tile/a/b/c"))
    }

    @Test
    fun mapTilerUrlHasTheTileAndKey() = assertEquals(
        "https://api.maptiler.com/maps/streets-v2/3/4/2.png?key=abc123",
        mapTilerUrl(Tile(3, 4, 2), "abc123"))

    @Test
    fun keyCannotChangeTheUrl() = assertTrue(mapTilerUrl(Tile(0, 0, 0), "abc&x=1#").endsWith("key=abcx1"))

    @Test
    fun keyIsRedactedFromLogText() =
        assertEquals("fetch https://api.maptiler.com/x.png?key=*** failed",
            redactKey("fetch https://api.maptiler.com/x.png?key=secret42 failed", "secret42"))

    @Test
    fun guestQuotaAllowsUpToTheLimit() {
        assertTrue(isWithinGuestQuota(1999, 2000))
        assertFalse(isWithinGuestQuota(2000, 2000))
    }

    @Test
    fun dailyCountsLoadOncePerUserAndDay() = runTest {
        val counts = DailyCounts()
        var loads = 0
        assertEquals(7, counts.count(1, "2026-11-25") { loads++; 7 })
        counts.increment(1, "2026-11-25")
        assertEquals(8, counts.count(1, "2026-11-25") { loads++; 0 })
        assertEquals(1, loads)
    }

    @Test
    fun newDayStartsFromTheDatabaseAgain() = runTest {
        val counts = DailyCounts()
        counts.count(1, "2026-11-25") { 50 }
        assertEquals(3, counts.count(1, "2026-11-26") { 3 })
    }
}

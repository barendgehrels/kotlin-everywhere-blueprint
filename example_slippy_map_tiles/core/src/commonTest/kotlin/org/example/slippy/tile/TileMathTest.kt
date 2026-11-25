package org.example.slippy.tile

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileMathTest {
    private fun assertNear(expected: Double, actual: Double, tolerance: Double = 1e-6) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, got $actual")

    @Test
    fun zoomZeroIsOneTile() {
        assertEquals(Tile(0, 0, 0), latLonToTile(LatLon(52.0, 5.0), 0))
        assertEquals(Tile(0, 0, 0), latLonToTile(LatLon(-80.0, -170.0), 0))
    }

    @Test
    fun originIsSouthEastQuadrantAtZoomOne() {
        // (0, 0) lies on the corner of four tiles; it belongs to the one to its south-east
        assertEquals(Tile(1, 1, 1), latLonToTile(LatLon(0.0, 0.0), 1))
    }

    @Test
    fun berlinAtZoomTen() {
        assertEquals(Tile(10, 550, 335), latLonToTile(LatLon(52.52, 13.405), 10))
    }

    @Test
    fun polesAreClampedToTheFirstAndLastRow() {
        assertEquals(0, latLonToTile(LatLon(90.0, 0.0), 5).y)
        assertEquals(31, latLonToTile(LatLon(-90.0, 0.0), 5).y)
    }

    @Test
    fun dateLineEastBelongsToLastColumn() {
        assertEquals(0, latLonToTile(LatLon(0.0, -180.0), 4).x)
        assertEquals(15, latLonToTile(LatLon(0.0, 180.0), 4).x)
    }

    @Test
    fun maxZoomHasNoOverflow() {
        val tile = latLonToTile(LatLon(-85.0511, 180.0), MAX_ZOOM)
        assertEquals(tileCount(MAX_ZOOM) - 1, tile.x)
        assertTrue(isTileValid(tile))
    }

    @Test
    fun boundsOfTheNorthWestTileAtZoomOne() {
        val bounds = tileBounds(Tile(1, 0, 0))
        assertNear(MAX_LATITUDE, bounds.north, 1e-4)
        assertNear(0.0, bounds.south)
        assertNear(-180.0, bounds.west)
        assertNear(0.0, bounds.east)
    }

    @Test
    fun pointLiesInsideTheBoundsOfItsTile() {
        val point = LatLon(-33.8568, 151.2153)
        for (z in MIN_ZOOM..MAX_ZOOM) {
            val position = latLonToTilePosition(point, z)
            val bounds = tileBounds(position.tile)
            assertTrue(point.lat in bounds.south..bounds.north, "z=$z: lat outside $bounds")
            assertTrue(point.lon in bounds.west..bounds.east, "z=$z: lon outside $bounds")
            assertTrue(position.fractionX in 0.0..1.0 && position.fractionY in 0.0..1.0)
        }
    }

    @Test
    fun fractionOfTheCenterOfATileIsAHalf() {
        val position = latLonToTilePosition(LatLon(0.0, 0.0), 0)
        assertNear(0.5, position.fractionX)
        assertNear(0.5, position.fractionY)
    }

    @Test
    fun invalidInputIsRefused() {
        assertFalse(isLatitudeValid(90.1))
        assertFalse(isLongitudeValid(-180.5))
        assertFalse(isZoomValid(23))
        assertFalse(isZoomValid(-1))
        assertFailsWith<IllegalArgumentException> { latLonToTile(LatLon(0.0, 0.0), 23) }
        assertFailsWith<IllegalArgumentException> { tileBounds(Tile(2, 4, 0)) }
    }

    @Test
    fun tileRangeDependsOnZoom() {
        assertTrue(isTileValid(Tile(2, 3, 3)))
        assertFalse(isTileValid(Tile(2, 4, 3)))
        assertFalse(isTileValid(Tile(2, -1, 0)))
    }

    @Test
    fun tilePathRoundTrips() {
        val tile = Tile(12, 2104, 1346)
        assertEquals("tile/12/2104/1346", tilePath(tile))
        assertEquals(tile, parseTilePath(tilePath(tile)))
        assertEquals(tile, parseTilePath("/tile/12/2104/1346"))
    }

    @Test
    fun tilePathRefusesWhatIsNoTile() {
        assertNull(parseTilePath("tile/1/2/3"))          // out of range
        assertNull(parseTilePath("tile/1/0"))
        assertNull(parseTilePath("tile/1/-0/0"))
        assertNull(parseTilePath("tile/1/0/0.png"))
        assertNull(parseTilePath("tiles/1/0/0"))
        assertNull(parseTilePath("tile/99999999/0/0"))
    }
}

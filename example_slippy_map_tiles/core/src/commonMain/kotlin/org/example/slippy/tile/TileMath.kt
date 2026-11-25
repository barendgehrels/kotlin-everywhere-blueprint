package org.example.slippy.tile

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

// See https://mapmath.dev/blog/lat-lon-to-tile-coordinates

const val MIN_ZOOM = 0
const val MAX_ZOOM = 22

/** Web Mercator stops here: the map is square. Latitudes beyond are clamped to it. */
const val MAX_LATITUDE = 85.0511

data class Tile(val z: Int, val x: Int, val y: Int)

data class LatLon(val lat: Double, val lon: Double)

data class TileBounds(val north: Double, val south: Double, val west: Double, val east: Double)

/** A tile, and where in it the point lies: 0..1 from the west edge, and 0..1 from the north edge. */
data class TilePosition(val tile: Tile, val fractionX: Double, val fractionY: Double)

fun isLatitudeValid(lat: Double): Boolean = lat in -90.0..90.0
fun isLongitudeValid(lon: Double): Boolean = lon in -180.0..180.0
fun isZoomValid(z: Int): Boolean = z in MIN_ZOOM..MAX_ZOOM

/** The number of tiles in each direction, at zoom [z]. */
fun tileCount(z: Int): Int = 1 shl z

/** z between 0 and 22; x and y between 0 and 2^z - 1. */
fun isTileValid(tile: Tile): Boolean =
    isZoomValid(tile.z) && tile.x in 0 until tileCount(tile.z) && tile.y in 0 until tileCount(tile.z)

fun clampLatitude(lat: Double): Double = lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)

/** The tile that contains the point, at zoom [z], and the point's place in it. */
fun latLonToTilePosition(latLon: LatLon, z: Int): TilePosition {
    require(isLatitudeValid(latLon.lat) && isLongitudeValid(latLon.lon) && isZoomValid(z)) {
        "Invalid input: $latLon, z=$z"
    }
    val n = tileCount(z)
    val latRad = clampLatitude(latLon.lat) * PI / 180.0
    val worldX = (latLon.lon + 180.0) / 360.0 * n
    val worldY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
    // The east edge (lon 180) and the south edge belong to the last tile
    val x = floor(worldX).toInt().coerceIn(0, n - 1)
    val y = floor(worldY).toInt().coerceIn(0, n - 1)
    return TilePosition(Tile(z, x, y), (worldX - x).coerceIn(0.0, 1.0), (worldY - y).coerceIn(0.0, 1.0))
}

fun latLonToTile(latLon: LatLon, z: Int): Tile = latLonToTilePosition(latLon, z).tile

/** The north-west corner of the tile grid at (x, y), which may be one past the last tile. */
private fun cornerLatLon(z: Int, x: Int, y: Int): LatLon {
    val n = tileCount(z).toDouble()
    val lon = x / n * 360.0 - 180.0
    val lat = atan(sinh(PI * (1.0 - 2.0 * y / n))) * 180.0 / PI
    return LatLon(lat, lon)
}

fun tileBounds(tile: Tile): TileBounds {
    require(isTileValid(tile)) { "Invalid tile: $tile" }
    val northWest = cornerLatLon(tile.z, tile.x, tile.y)
    val southEast = cornerLatLon(tile.z, tile.x + 1, tile.y + 1)
    return TileBounds(north = northWest.lat, south = southEast.lat, west = northWest.lon, east = southEast.lon)
}

/** The path of the service's tile endpoint (a GET): `tile/{z}/{x}/{y}`. */
fun tilePath(tile: Tile): String = "tile/${tile.z}/${tile.x}/${tile.y}"

/** Reads a path such as `tile/3/4/2`; null when it is not one, or not a valid tile. */
fun parseTilePath(path: String): Tile? {
    val parts = path.trim('/').split('/')
    if (parts.size != 4 || parts[0] != "tile") return null
    val numbers = parts.drop(1).map { part ->
        if (part.isEmpty() || part.length > 7 || !part.all { it in '0'..'9' }) return null
        part.toInt()
    }
    return Tile(numbers[0], numbers[1], numbers[2]).takeIf(::isTileValid)
}

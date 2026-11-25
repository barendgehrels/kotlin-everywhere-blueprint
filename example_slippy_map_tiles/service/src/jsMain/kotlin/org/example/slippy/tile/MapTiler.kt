package org.example.slippy.tile

// MapTiler's raster tiles. The URL holds the API key: never log it, nor MapTiler's text.

/** The provider's URL of a tile (512 px PNG). */
fun mapTilerUrl(tile: Tile, apiKey: String): String =
    "https://api.maptiler.com/maps/streets-v2/${tile.z}/${tile.x}/${tile.y}.png?key=${encodeKey(apiKey)}"

private fun encodeKey(apiKey: String): String = apiKey.filter { it.isLetterOrDigit() || it in "-_." }

/** A text for the log, with the key taken out in case a runtime put the URL in it. */
fun redactKey(text: String, apiKey: String): String =
    if (apiKey.isEmpty()) text else text.replace(apiKey, "***")

/** The tile of a request path such as "/tile/3/4/2"; null when it isn't a valid one. */
fun tileOfPath(path: String): Tile? = parseTilePath(path.removePrefix("/"))

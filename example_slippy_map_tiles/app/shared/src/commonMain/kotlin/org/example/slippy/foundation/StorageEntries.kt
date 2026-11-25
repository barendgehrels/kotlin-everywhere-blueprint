package org.example.slippy.foundation

import kotlinx.serialization.json.Json
import org.example.slippy.tile.TileInput

object StorageEntries {
    val SESSION = jsonEntry<Session>("session")
    val KNOWN_GUESTS = jsonEntry<List<KnownGuest>>("known_guests")
    val TILE_INPUT = jsonEntry<TileInput>("tile_input")
}

private val storageJson = Json { ignoreUnknownKeys = true }

private inline fun <reified T> jsonEntry(name: String) = StorageEntry<T>(
    name = name,
    serialize = { storageJson.encodeToString(it) },
    deserialize = { storageJson.decodeFromString(it) },
)

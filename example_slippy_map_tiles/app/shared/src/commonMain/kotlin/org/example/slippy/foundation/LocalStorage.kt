package org.example.slippy.foundation

import kotlinx.coroutines.CancellationException

/** A typed entry of local storage: all of them are in [StorageEntries]. */
class StorageEntry<T>(val name: String, val serialize: (T) -> String, val deserialize: (String) -> T)

/** What a platform stores: text by name. */
interface KeyValueStore {
    suspend fun get(name: String): String?
    suspend fun set(name: String, value: String)
}

/** DataStore on Android, iOS and the desktop; the browser's localStorage on the web. */
expect fun platformStore(): KeyValueStore

/**
 * Typed local storage. A class around the store, made once in the AppContainer, so tests can give
 * it a store in memory.
 */
class LocalStorage(private val store: KeyValueStore) {
    suspend fun <T> get(entry: StorageEntry<T>): T? {
        val text = try {
            store.get(entry.name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Local storage cannot be read: $e")
            null
        } ?: return null
        return try {
            entry.deserialize(text)
        } catch (e: Exception) {
            // Such as a renamed enum constant: start without the old value
            log("Stored ${entry.name} no longer reads: $e")
            null
        }
    }

    suspend fun <T> set(entry: StorageEntry<T>, value: T) {
        try {
            store.set(entry.name, entry.serialize(value))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Local storage cannot be written: $e")
        }
    }
}

/** A store in memory, for tests and previews. */
class MemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override suspend fun get(name: String): String? = values[name]
    override suspend fun set(name: String, value: String) {
        values[name] = value
    }
}

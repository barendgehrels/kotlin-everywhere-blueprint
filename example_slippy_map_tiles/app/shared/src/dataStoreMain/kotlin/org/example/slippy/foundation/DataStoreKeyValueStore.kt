package org.example.slippy.foundation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath

/** The file name; Android's backup rules exclude it, because it holds the session's tokens. */
const val DATA_STORE_FILE_NAME = "slippy.preferences_pb"

/** DataStore, once, for Android, iOS and the desktop. Each platform gives only the path. */
class DataStoreKeyValueStore(path: String) : KeyValueStore {
    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

    override suspend fun get(name: String): String? = dataStore.data.first()[stringPreferencesKey(name)]

    override suspend fun set(name: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(name)] = value }
    }
}

package org.example.slippy.foundation

import androidx.compose.runtime.Composable
import web.storage.localStorage

private object WebPlatform : Platform {
    override fun log(message: String) = println(message)

    @Composable
    override fun getVersionName(): String = Configuration.VERSION_NAME

    override fun reachableUrl(url: String): String = url
}

actual fun getPlatform(): Platform = WebPlatform

/** The browser's localStorage: not cookies, which travel with every request and break on ';'. */
private object BrowserKeyValueStore : KeyValueStore {
    override suspend fun get(name: String): String? = localStorage.getItem(name)

    override suspend fun set(name: String, value: String) {
        localStorage.setItem(name, value)
    }
}

actual fun platformStore(): KeyValueStore = BrowserKeyValueStore

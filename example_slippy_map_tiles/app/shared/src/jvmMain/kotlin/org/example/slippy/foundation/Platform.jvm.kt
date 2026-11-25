package org.example.slippy.foundation

import androidx.compose.runtime.Composable
import java.io.File

private object DesktopPlatform : Platform {
    override fun log(message: String) = println(message)

    @Composable
    override fun getVersionName(): String = Configuration.VERSION_NAME

    override fun reachableUrl(url: String): String = url
}

actual fun getPlatform(): Platform = DesktopPlatform

actual fun platformStore(): KeyValueStore {
    val folder = File(System.getProperty("user.home"), ".slippy").apply { mkdirs() }
    return DataStoreKeyValueStore(File(folder, DATA_STORE_FILE_NAME).absolutePath)
}

package org.example.slippy.foundation

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

private object IosPlatform : Platform {
    override fun log(message: String) {
        // Not NSLog("%@", message): Kotlin/Native passes a String in a C vararg list as a C
        // string, not as an NSString, and %@ then crashes. The message itself is the format.
        NSLog(message.replace("%", "%%"))
    }

    @Composable
    override fun getVersionName(): String = Configuration.VERSION_NAME

    override fun reachableUrl(url: String): String = url
}

actual fun getPlatform(): Platform = IosPlatform

/** In Application Support, not in Documents, and excluded from backups: it holds the tokens. */
@OptIn(ExperimentalForeignApi::class)
actual fun platformStore(): KeyValueStore {
    val fileManager = NSFileManager.defaultManager
    val folder: NSURL = checkNotNull(
        fileManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
    ) { "No Application Support folder" }
    fileManager.createDirectoryAtURL(folder, withIntermediateDirectories = true, attributes = null, error = null)
    folder.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
    val path = checkNotNull(folder.URLByAppendingPathComponent(DATA_STORE_FILE_NAME)?.path)
    return DataStoreKeyValueStore(path)
}

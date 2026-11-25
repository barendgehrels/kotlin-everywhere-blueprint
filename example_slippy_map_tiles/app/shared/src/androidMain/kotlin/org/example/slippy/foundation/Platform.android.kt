package org.example.slippy.foundation

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

// The application context, for the storage path. Set once by MainActivity, before the app starts.
@SuppressLint("StaticFieldLeak")
private var appContext: Context? = null

fun initAndroidPlatform(context: Context) {
    appContext = context.applicationContext
}

private object AndroidPlatform : Platform {
    override fun log(message: String) {
        Log.i("Slippy", message)
    }

    @Composable
    override fun getVersionName(): String {
        if (LocalInspectionMode.current) return "preview"
        return getPackageVersionName(LocalContext.current)
    }

    override fun reachableUrl(url: String): String =
        url.replace("://localhost", "://10.0.2.2").replace("://127.0.0.1", "://10.0.2.2")
}

private fun getPackageVersionName(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: Configuration.VERSION_NAME

actual fun getPlatform(): Platform = AndroidPlatform

actual fun platformStore(): KeyValueStore {
    val context = checkNotNull(appContext) { "initAndroidPlatform(context) was not called" }
    return DataStoreKeyValueStore(context.filesDir.resolve(DATA_STORE_FILE_NAME).absolutePath)
}

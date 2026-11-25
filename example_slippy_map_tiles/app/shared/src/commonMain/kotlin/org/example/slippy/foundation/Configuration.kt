package org.example.slippy.foundation

import generated.AppConfig

object Configuration {
    /** The service's URL, without a trailing slash. */
    val csUrl: String by lazy { getPlatform().reachableUrl(AppConfig.CLOUD_SERVICE_URL).trimEnd('/') }

    const val VERSION_NAME: String = AppConfig.VERSION_NAME
}

/** Feature switches of the app. */
object Features {
    /** Guests: the Guest button shows where there is a human check too ([hasHumanCheck]). */
    const val guests = true
}

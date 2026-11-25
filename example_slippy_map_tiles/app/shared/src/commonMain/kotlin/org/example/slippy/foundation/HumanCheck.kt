package org.example.slippy.foundation

import androidx.compose.runtime.Composable

/** The token: "" without a site key or when the check failed; null: no check on this platform. */
expect suspend fun humanCheckToken(): String?

/** Only where there is a check, the Guest button shows. */
expect val hasHumanCheck: Boolean

/** Where the check is shown, if the platform shows it itself (Android, iOS). Once, at the top of the app. */
@Composable
expect fun HumanCheckHost()

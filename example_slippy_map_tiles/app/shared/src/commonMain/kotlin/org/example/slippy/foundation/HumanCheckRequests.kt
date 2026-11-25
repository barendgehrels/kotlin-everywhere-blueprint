package org.example.slippy.foundation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.example.slippy.resources.Res
import org.example.slippy.resources.human_check_title
import org.jetbrains.compose.resources.stringResource

/**
 * The native apps' human check (Android, iOS): the worker's page `/human_check` in a web view in a
 * dialog. [token] waits for the bridge of that page.
 */
object HumanCheckRequests {
    val pending: StateFlow<CompletableDeferred<String>?>
        field = MutableStateFlow(null)

    suspend fun token(): String {
        val request = CompletableDeferred<String>()
        pending.value = request
        try {
            return withTimeoutOrNull(3.minutes) { request.await() } ?: ""
        } finally {
            pending.compareAndSet(request, null)
        }
    }
}

/** The page's starting height: the widget is 65 high. */
private const val START_HEIGHT = 72

/**
 * The dialog with the platform's web view. The web view gets the URL, and calls back with the
 * token and with the page's height (CSS pixels: dp on Android, points on iOS).
 */
@Composable
internal fun HumanCheckDialog(
    webView: @Composable (modifier: Modifier, url: String, onToken: (String) -> Unit, onHeight: (Int) -> Unit) -> Unit,
) {
    val request by HumanCheckRequests.pending.collectAsState()
    val current = request ?: return
    // A new web view per request, so each bridge answers its own request
    key(current) {
        var height by remember { mutableIntStateOf(START_HEIGHT) }
        // Dismissing fails the check, and the app says so
        Dialog(onDismissRequest = { current.complete("") }) {
            Surface(shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(Res.string.human_check_title), style = MaterialTheme.typography.titleSmall)
                    webView(
                        Modifier.fillMaxWidth().height(height.dp),
                        "${Configuration.csUrl}/human_check",
                        { token -> current.complete(token) },
                        { newHeight -> height = maxOf(START_HEIGHT, newHeight) },
                    )
                }
            }
        }
    }
}

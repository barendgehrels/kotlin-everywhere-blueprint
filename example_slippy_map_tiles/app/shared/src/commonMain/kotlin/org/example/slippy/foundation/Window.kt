package org.example.slippy.foundation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * The one function for every layout decision, so the navigation and the screens switch at the same
 * moment. By orientation, not by width: on a phone in landscape, height is what is scarce.
 */
@Composable
fun isPortraitWindow(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return size.height >= size.width
}

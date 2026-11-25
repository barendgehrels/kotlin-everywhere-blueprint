package org.example.slippy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.slippy.account.AccountScreen
import org.example.slippy.foundation.HumanCheckHost
import org.example.slippy.foundation.LocalDarkTheme
import org.example.slippy.foundation.MessageStore
import org.example.slippy.foundation.Screen
import org.example.slippy.foundation.SlippyTheme
import org.example.slippy.foundation.SlippyViewModel
import org.example.slippy.foundation.isPortraitWindow
import org.example.slippy.resources.Res
import org.example.slippy.resources.ic_account
import org.example.slippy.resources.ic_tile
import org.example.slippy.resources.screen_account
import org.example.slippy.resources.screen_tile
import org.example.slippy.tile.TileScreen
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The app, on every platform. [darkTheme] is the startup option `theme`; null follows the system. */
@Composable
fun App(darkTheme: Boolean? = null) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        SlippyTheme {
            SlippyApp()
        }
    }
}

@Composable
private fun SlippyApp(appViewModel: SlippyViewModel = viewModel { SlippyViewModel() }) {
    val container = appViewModel.appContainer
    val activeScreen by appViewModel.activeScreen.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isPortrait = isPortraitWindow()

    Messages(container.messages, snackbarHostState)
    HumanCheckHost()

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                val isError = (data.visuals as? MessageVisuals)?.isError == true
                Snackbar(
                    snackbarData = data,
                    containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.inverseSurface,
                    contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        },
        // A bar in portrait and a rail in landscape: on a phone in landscape, height is scarce
        bottomBar = {
            if (isPortrait) {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = screen == activeScreen,
                            onClick = { appViewModel.setActiveScreen(screen) },
                            icon = { Icon(painterResource(screen.icon), contentDescription = null) },
                            label = { Text(stringResource(screen.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (!isPortrait) {
                NavigationRail {
                    Screen.entries.forEach { screen ->
                        NavigationRailItem(
                            selected = screen == activeScreen,
                            onClick = { appViewModel.setActiveScreen(screen) },
                            icon = { Icon(painterResource(screen.icon), contentDescription = null) },
                            label = { Text(stringResource(screen.label)) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                when (activeScreen) {
                    Screen.Tile -> TileScreen(
                        tiles = container.tiles,
                        geocode = container.geocode,
                        session = container.session,
                        storage = container.localStorage,
                        messages = container.messages,
                    )
                    Screen.Account -> AccountScreen(
                        gateway = container.accounts,
                        session = container.session,
                        messages = container.messages,
                    )
                }
            }
        }
    }
}

private val Screen.label: StringResource
    get() = when (this) {
        Screen.Tile -> Res.string.screen_tile
        Screen.Account -> Res.string.screen_account
    }

private val Screen.icon: DrawableResource
    get() = when (this) {
        Screen.Tile -> Res.drawable.ic_tile
        Screen.Account -> Res.drawable.ic_account
    }

private class MessageVisuals(
    override val message: String,
    val isError: Boolean,
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = if (isError) SnackbarDuration.Long else SnackbarDuration.Short
}

/** The first message of the queue, in the snackbar; then the next. */
@Composable
private fun Messages(messages: MessageStore, snackbarHostState: SnackbarHostState) {
    val queue by messages.messages.collectAsState()
    val first = queue.firstOrNull() ?: return
    val text = first.text ?: first.resId?.let { stringResource(it) } ?: ""
    LaunchedEffect(first.id) {
        snackbarHostState.showSnackbar(MessageVisuals(text, first.isError))
        messages.shown(first)
    }
}

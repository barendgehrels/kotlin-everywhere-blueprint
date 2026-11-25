package org.example.slippy.tile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.slippy.foundation.LocalStorage
import org.example.slippy.foundation.MessageStore
import org.example.slippy.foundation.SessionStore
import org.example.slippy.foundation.isPortraitWindow
import org.example.slippy.geocode.GeocodeGateway

@Composable
fun TileScreen(
    tiles: TileGateway,
    geocode: GeocodeGateway,
    session: SessionStore,
    storage: LocalStorage,
    messages: MessageStore,
    viewModel: TileViewModel = viewModel { TileViewModel(tiles, geocode, storage, messages) },
) {
    val uiState by viewModel.tileUiState.collectAsState()
    val currentSession by session.session.collectAsState()
    val hasSession = currentSession.hasSession
    // The place name (AI) is for a registered user with a verified email only
    val canGeocode = currentSession.isRegistered && currentSession.emailVerified
    LaunchedEffect(hasSession, canGeocode) {
        viewModel.onAction(TileAction.SessionChanged(hasSession, canGeocode))
    }
    TileContent(uiState = uiState, isPortrait = isPortraitWindow(), onAction = viewModel::onAction)
}

package org.example.slippy.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.slippy.foundation.Features
import org.example.slippy.foundation.MessageStore
import org.example.slippy.foundation.SessionStore
import org.example.slippy.foundation.getPlatform
import org.example.slippy.foundation.hasHumanCheck

@Composable
fun AccountScreen(
    gateway: AccountGateway,
    session: SessionStore,
    messages: MessageStore,
    viewModel: AccountViewModel = viewModel { AccountViewModel(gateway, session, messages) },
) {
    val uiState by viewModel.accountUiState.collectAsState()
    val currentSession by session.session.collectAsState()
    AccountContent(
        uiState = uiState,
        session = currentSession,
        showGuestButton = Features.guests && hasHumanCheck,
        versionName = getPlatform().getVersionName(),
        onAction = viewModel::onAction,
    )
}

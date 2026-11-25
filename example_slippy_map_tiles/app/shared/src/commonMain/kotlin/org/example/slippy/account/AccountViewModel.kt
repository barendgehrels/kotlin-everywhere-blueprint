package org.example.slippy.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.slippy.foundation.MessageStore
import org.example.slippy.foundation.SessionStore
import org.example.slippy.resources.Res
import org.example.slippy.resources.error_email_invalid
import org.example.slippy.resources.error_guest_name
import org.example.slippy.resources.error_password_invalid
import org.example.slippy.resources.message_password_changed
import org.example.slippy.resources.message_password_reset_sent
import org.example.slippy.resources.message_verification_sent
import org.example.slippy.resources.message_verified
import org.example.slippy.resources.message_not_verified_yet
import org.jetbrains.compose.resources.StringResource

data class AccountUiState(
    val login: String = "",                     // an email address, or a guest name
    val password: String = "",
    val registerEmail: String = "",             // a guest's: its login field holds the name
    val registerPassword: String = "",
    val newPassword: String = "",
    val loginError: StringResource? = null,
    val passwordError: StringResource? = null,
    val registerEmailError: StringResource? = null,
    val registerPasswordError: StringResource? = null,
    val newPasswordError: StringResource? = null,
    val isChangingPassword: Boolean = false,
    val isRegisteringGuest: Boolean = false,
    val isBusy: Boolean = false,
)

sealed interface AccountAction {
    data class EditLogin(val text: String) : AccountAction
    data class EditPassword(val text: String) : AccountAction
    data class EditRegisterEmail(val text: String) : AccountAction
    data class EditRegisterPassword(val text: String) : AccountAction
    data class EditNewPassword(val text: String) : AccountAction
    data object LogIn : AccountAction
    data object Register : AccountAction
    data object Guest : AccountAction
    data object ResetPassword : AccountAction
    data object ResendVerification : AccountAction
    data object RefreshVerification : AccountAction
    data class ShowChangePassword(val show: Boolean) : AccountAction
    data object ChangePassword : AccountAction
    data class ShowGuestRegistration(val show: Boolean) : AccountAction
    data object RegisterGuest : AccountAction
    data object LogOut : AccountAction
}

class AccountViewModel(
    private val gateway: AccountGateway,
    private val session: SessionStore,
    private val messages: MessageStore,
) : ViewModel() {
    val accountUiState: StateFlow<AccountUiState>
        field = MutableStateFlow(AccountUiState())

    fun onAction(action: AccountAction) = when (action) {
        is AccountAction.EditLogin -> accountUiState.update { it.copy(login = action.text.trim(), loginError = null) }
        is AccountAction.EditPassword -> accountUiState.update { it.copy(password = action.text, passwordError = null) }
        is AccountAction.EditRegisterEmail ->
            accountUiState.update { it.copy(registerEmail = action.text.trim(), registerEmailError = null) }
        is AccountAction.EditRegisterPassword ->
            accountUiState.update { it.copy(registerPassword = action.text, registerPasswordError = null) }
        is AccountAction.EditNewPassword ->
            accountUiState.update { it.copy(newPassword = action.text, newPasswordError = null) }
        AccountAction.LogIn -> logIn()
        AccountAction.Register -> register()
        AccountAction.Guest -> guest()
        AccountAction.ResetPassword -> resetPassword()
        AccountAction.ResendVerification -> resendVerification()
        AccountAction.RefreshVerification -> refreshVerification()
        is AccountAction.ShowChangePassword ->
            accountUiState.update { it.copy(isChangingPassword = action.show, newPassword = "", newPasswordError = null) }
        AccountAction.ChangePassword -> changePassword()
        is AccountAction.ShowGuestRegistration -> accountUiState.update {
            it.copy(isRegisteringGuest = action.show, registerEmailError = null, registerPasswordError = null)
        }
        AccountAction.RegisterGuest -> registerGuest()
        AccountAction.LogOut -> logOut()
    }

    private fun logIn() {
        val state = accountUiState.value
        if (!checkEmailAndPassword(state.login, state.password)) return
        busy {
            gateway.logIn(state.login, state.password).fold(
                onSuccess = { session.setSession(it); clearPasswords() },
                onFailure = { messages.show(it) },
            )
        }
    }

    private fun register() {
        val state = accountUiState.value
        if (!checkEmailAndPassword(state.login, state.password)) return
        busy {
            gateway.register(state.login, state.password).fold(
                onSuccess = { session.setSession(it); clearPasswords(); sendVerificationEmail() },
                onFailure = { messages.show(it) },
            )
        }
    }

    private fun guest() {
        val name = accountUiState.value.login
        if (!isGuestNameValid(name)) {
            accountUiState.update { it.copy(loginError = Res.string.error_guest_name) }
            return
        }
        busy {
            session.startGuest(name).fold(
                onSuccess = { clearPasswords() },
                onFailure = { messages.show(it) },
            )
        }
    }

    /** The guest becomes a registered user; its token is refreshed first, so the service gets a valid one. */
    private fun registerGuest() {
        val state = accountUiState.value
        val emailError = if (isEmailValid(state.registerEmail)) null else Res.string.error_email_invalid
        val passwordError = if (isPasswordValid(state.registerPassword)) null else Res.string.error_password_invalid
        if (emailError != null || passwordError != null) {
            accountUiState.update { it.copy(registerEmailError = emailError, registerPasswordError = passwordError) }
            return
        }
        busy {
            val refreshed = session.refreshNow()
            val result = refreshed.exceptionOrNull()?.let { Result.failure(it) }
                ?: gateway.register(state.registerEmail, state.registerPassword)
            result.fold(
                onSuccess = {
                    session.setSession(it)
                    accountUiState.update { s ->
                        s.copy(isRegisteringGuest = false, registerEmail = "", registerPassword = "", login = state.registerEmail)
                    }
                    sendVerificationEmail()
                },
                onFailure = { messages.show(it) },
            )
        }
    }

    private fun resetPassword() {
        val email = accountUiState.value.login
        if (!isEmailValid(email)) {
            accountUiState.update { it.copy(loginError = Res.string.error_email_invalid) }
            return
        }
        busy {
            gateway.resetPassword(email).fold(
                onSuccess = { messages.show(Res.string.message_password_reset_sent) },
                onFailure = { messages.show(it) },
            )
        }
    }

    private fun resendVerification() = busy { sendVerificationEmail() }

    private fun refreshVerification() = busy {
        session.refreshNow().fold(
            onSuccess = {
                val verified = session.session.value.emailVerified
                messages.show(if (verified) Res.string.message_verified else Res.string.message_not_verified_yet)
            },
            onFailure = { messages.show(it) },
        )
    }

    private fun changePassword() {
        val password = accountUiState.value.newPassword
        if (!isPasswordValid(password)) {
            accountUiState.update { it.copy(newPasswordError = Res.string.error_password_invalid) }
            return
        }
        busy {
            gateway.changePassword(password).fold(
                onSuccess = {
                    session.setSession(it)
                    accountUiState.update { s -> s.copy(isChangingPassword = false, newPassword = "") }
                    messages.show(Res.string.message_password_changed)
                },
                onFailure = { messages.show(it) },
            )
        }
    }

    private fun logOut() {
        session.logOut()
        accountUiState.value = AccountUiState()
    }

    private suspend fun sendVerificationEmail() {
        gateway.sendVerificationEmail().fold(
            onSuccess = { messages.show(Res.string.message_verification_sent) },
            onFailure = { messages.show(it) },
        )
    }

    private fun checkEmailAndPassword(email: String, password: String): Boolean {
        val emailError = if (isEmailValid(email)) null else Res.string.error_email_invalid
        val passwordError = if (isPasswordValid(password)) null else Res.string.error_password_invalid
        if (emailError == null && passwordError == null) return true
        accountUiState.update { it.copy(loginError = emailError, passwordError = passwordError) }
        return false
    }

    private fun clearPasswords() {
        accountUiState.update { it.copy(password = "", registerPassword = "", newPassword = "") }
    }

    /** One call at a time: the buttons are disabled meanwhile. */
    private fun busy(block: suspend () -> Unit) {
        if (accountUiState.value.isBusy) return
        accountUiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                accountUiState.update { it.copy(isBusy = false) }
            }
        }
    }
}

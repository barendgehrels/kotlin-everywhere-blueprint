package org.example.slippy.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.example.slippy.foundation.Session
import org.example.slippy.foundation.SlippyTheme
import org.example.slippy.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountContent(
    uiState: AccountUiState,
    session: Session,
    showGuestButton: Boolean,
    versionName: String,
    onAction: (AccountAction) -> Unit,
) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.account_title), style = MaterialTheme.typography.headlineSmall)
            when {
                !session.hasSession -> SignedOut(uiState, showGuestButton, onAction)
                session.isGuest -> GuestAccount(uiState, session, onAction)
                else -> RegisteredAccount(uiState, session, onAction)
            }
            HorizontalDivider()
            Text(
                stringResource(Res.string.version, versionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColumnScope.SignedOut(uiState: AccountUiState, showGuestButton: Boolean, onAction: (AccountAction) -> Unit) {
    Field(
        value = uiState.login,
        label = Res.string.login_field,
        error = uiState.loginError,
        keyboardType = KeyboardType.Email,
        onValueChange = { onAction(AccountAction.EditLogin(it)) },
    )
    PasswordField(uiState.password, Res.string.password_field, uiState.passwordError) {
        onAction(AccountAction.EditPassword(it))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onAction(AccountAction.LogIn) }, enabled = !uiState.isBusy) {
            Text(stringResource(Res.string.log_in))
        }
        OutlinedButton(onClick = { onAction(AccountAction.Register) }, enabled = !uiState.isBusy) {
            Text(stringResource(Res.string.register))
        }
        if (showGuestButton) {
            OutlinedButton(onClick = { onAction(AccountAction.Guest) }, enabled = !uiState.isBusy) {
                Text(stringResource(Res.string.guest))
            }
        }
    }
    Explanation(
        buildList {
            if (showGuestButton) add(Res.string.explain_guest)
            add(Res.string.explain_register)
            add(Res.string.explain_keep)
        }
    )
    TextButton(onClick = { onAction(AccountAction.ResetPassword) }, enabled = !uiState.isBusy) {
        Text(stringResource(Res.string.reset_password))
    }
}

@Composable
private fun ColumnScope.RegisteredAccount(uiState: AccountUiState, session: Session, onAction: (AccountAction) -> Unit) {
    Text(stringResource(Res.string.logged_in_as, session.email.orEmpty()))
    if (session.emailVerified) {
        Text(stringResource(Res.string.email_verified), color = MaterialTheme.colorScheme.primary)
    } else {
        Text(stringResource(Res.string.email_not_verified), color = MaterialTheme.colorScheme.error)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onAction(AccountAction.ResendVerification) }, enabled = !uiState.isBusy) {
                Text(stringResource(Res.string.resend_verification))
            }
            OutlinedButton(onClick = { onAction(AccountAction.RefreshVerification) }, enabled = !uiState.isBusy) {
                Text(stringResource(Res.string.refresh_verification))
            }
        }
    }
    if (uiState.isChangingPassword) {
        PasswordField(uiState.newPassword, Res.string.new_password_field, uiState.newPasswordError) {
            onAction(AccountAction.EditNewPassword(it))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onAction(AccountAction.ChangePassword) }, enabled = !uiState.isBusy) {
                Text(stringResource(Res.string.change_password))
            }
            TextButton(onClick = { onAction(AccountAction.ShowChangePassword(false)) }) {
                Text(stringResource(Res.string.cancel))
            }
        }
    } else {
        OutlinedButton(onClick = { onAction(AccountAction.ShowChangePassword(true)) }, enabled = !uiState.isBusy) {
            Text(stringResource(Res.string.change_password))
        }
    }
    Button(onClick = { onAction(AccountAction.LogOut) }) {
        Text(stringResource(Res.string.log_out))
    }
}

@Composable
private fun ColumnScope.GuestAccount(uiState: AccountUiState, session: Session, onAction: (AccountAction) -> Unit) {
    Text(stringResource(Res.string.guest_as, session.name.orEmpty()))
    if (uiState.isRegisteringGuest) {
        Field(
            value = uiState.registerEmail,
            label = Res.string.email_field,
            error = uiState.registerEmailError,
            keyboardType = KeyboardType.Email,
            onValueChange = { onAction(AccountAction.EditRegisterEmail(it)) },
        )
        PasswordField(uiState.registerPassword, Res.string.password_field, uiState.registerPasswordError) {
            onAction(AccountAction.EditRegisterPassword(it))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onAction(AccountAction.RegisterGuest) }, enabled = !uiState.isBusy) {
                Text(stringResource(Res.string.register))
            }
            TextButton(onClick = { onAction(AccountAction.ShowGuestRegistration(false)) }) {
                Text(stringResource(Res.string.cancel))
            }
        }
    } else {
        OutlinedButton(onClick = { onAction(AccountAction.ShowGuestRegistration(true)) }, enabled = !uiState.isBusy) {
            Text(stringResource(Res.string.guest_to_registration))
        }
    }
    Explanation(listOf(Res.string.explain_register, Res.string.explain_keep))
    Button(onClick = { onAction(AccountAction.LogOut) }) {
        Text(stringResource(Res.string.log_out))
    }
}

@Composable
private fun Field(
    value: String,
    label: StringResource,
    error: StringResource?,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it)) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(value: String, label: StringResource, error: StringResource?, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it)) } },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** What each choice gives: small and gray. */
@Composable
private fun Explanation(lines: List<StringResource>) {
    Column {
        lines.forEach {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
fun AccountPreview() = SlippyTheme {
    AccountContent(
        uiState = AccountUiState(login = "Explorer42"),
        session = Session(),
        showGuestButton = true,
        versionName = "0.1.0",
        onAction = {},
    )
}

@Preview
@Composable
fun AccountGuestPreview() = SlippyTheme {
    AccountContent(
        uiState = AccountUiState(isRegisteringGuest = true, registerEmail = "explorer@example.com"),
        session = Session(idToken = "token", isGuest = true, name = "Explorer42"),
        showGuestButton = true,
        versionName = "0.1.0",
        onAction = {},
    )
}

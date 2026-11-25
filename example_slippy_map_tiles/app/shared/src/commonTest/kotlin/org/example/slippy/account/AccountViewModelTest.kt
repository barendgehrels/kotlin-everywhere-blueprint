package org.example.slippy.account

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.example.slippy.foundation.ApiException
import org.example.slippy.foundation.ERROR_CODE_LOGIN
import org.example.slippy.foundation.FakeSessionGateway
import org.example.slippy.foundation.LocalStorage
import org.example.slippy.foundation.MemoryKeyValueStore
import org.example.slippy.foundation.MessageStore
import org.example.slippy.foundation.Session
import org.example.slippy.foundation.SessionStore
import org.example.slippy.foundation.storeScope
import org.example.slippy.foundation.errorResource
import org.example.slippy.resources.Res
import org.example.slippy.resources.error_guest_name

private class FakeAccountGateway : AccountGateway {
    var loginResult: Result<Session> = Result.success(Session(idToken = "id", refreshToken = "refresh", email = "ann@example.com"))
    val logins = mutableListOf<Pair<String, String>>()

    override suspend fun logIn(email: String, password: String): Result<Session> {
        logins += email to password
        return loginResult
    }

    override suspend fun register(email: String, password: String) = loginResult
    override suspend fun sendVerificationEmail() = Result.success(Unit)
    override suspend fun resetPassword(email: String) = Result.success(Unit)
    override suspend fun changePassword(password: String) = loginResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val gateway = FakeAccountGateway()
    private val sessionGateway = FakeSessionGateway()
    private val messages = MessageStore()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.sessionStore() = SessionStore(
        sessionGateway, messages, LocalStorage(MemoryKeyValueStore()), storeScope(), { "human-token" })

    private fun AccountViewModel.typeLogin(login: String, password: String) {
        onAction(AccountAction.EditLogin(login))
        onAction(AccountAction.EditPassword(password))
    }

    @Test
    fun loginSetsTheSession() = runTest(dispatcher) {
        val session = sessionStore()
        val viewModel = AccountViewModel(gateway, session, messages)
        viewModel.typeLogin("ann@example.com", "secret1")
        viewModel.onAction(AccountAction.LogIn)
        advanceUntilIdle()

        assertEquals("id", session.session.value.idToken)
        assertEquals("", viewModel.accountUiState.value.password)
        assertFalse(viewModel.accountUiState.value.isBusy)
        assertTrue(messages.messages.value.isEmpty())
    }

    @Test
    fun failedLoginShowsTheServiceErrorAsOwnText() = runTest(dispatcher) {
        gateway.loginResult = Result.failure(ApiException(ERROR_CODE_LOGIN, "INVALID_PASSWORD", 400))
        val session = sessionStore()
        val viewModel = AccountViewModel(gateway, session, messages)
        viewModel.typeLogin("ann@example.com", "wrong12")
        viewModel.onAction(AccountAction.LogIn)
        advanceUntilIdle()

        assertFalse(session.session.value.hasSession)
        assertEquals(errorResource(ERROR_CODE_LOGIN), messages.messages.value.single().resId)
        assertFalse(viewModel.accountUiState.value.isBusy)
    }

    @Test
    fun invalidEmailIsRefusedBeforeTheCall() = runTest(dispatcher) {
        val viewModel = AccountViewModel(gateway, sessionStore(), messages)
        viewModel.typeLogin("Explorer", "secret1")
        viewModel.onAction(AccountAction.LogIn)
        advanceUntilIdle()

        assertNotNull(viewModel.accountUiState.value.loginError)
        assertTrue(gateway.logins.isEmpty())
    }

    @Test
    fun guestStartsWithTheHumanCheckToken() = runTest(dispatcher) {
        val session = sessionStore()
        val viewModel = AccountViewModel(gateway, session, messages)
        viewModel.onAction(AccountAction.EditLogin("Explorer"))
        viewModel.onAction(AccountAction.Guest)
        advanceUntilIdle()

        assertEquals(listOf("human-token" to "Explorer"), sessionGateway.guestCalls)
        assertTrue(session.session.value.isGuest)
        assertEquals("Explorer", session.session.value.name)
    }

    @Test
    fun emailIsNoGuestName() = runTest(dispatcher) {
        val viewModel = AccountViewModel(gateway, sessionStore(), messages)
        viewModel.onAction(AccountAction.EditLogin("ann@example.com"))
        viewModel.onAction(AccountAction.Guest)
        advanceUntilIdle()

        assertEquals(Res.string.error_guest_name, viewModel.accountUiState.value.loginError)
        assertTrue(sessionGateway.guestCalls.isEmpty())
    }
}

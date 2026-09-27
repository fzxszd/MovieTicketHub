package me.ibrahim.moviesapp.compose.presentation.login

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.ibrahim.moviesapp.compose.domain.auth.*
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun registrationModeShowsHiddenPasswordConfirmationAndValidation() {
        compose.setContent { LoginScreen(LoginViewModel(UiFakeAuthRepository())) {} }
        compose.onNodeWithText("还没有账号？去注册").performClick()
        compose.onNodeWithText("确认密码").assertExists()
        compose.onNodeWithText("注册").performClick()
        compose.onNodeWithText("请输入有效邮箱").assertExists()
        compose.onNodeWithText("密码长度必须为 8–64 个字符").assertExists()
    }
}

private class UiFakeAuthRepository : AuthRepository {
    private val mutableState = MutableStateFlow<AuthState>(AuthState.Guest)
    override val state: StateFlow<AuthState> = mutableState
    override val pendingAction = MutableStateFlow<PendingAuthAction?>(null)
    override suspend fun register(username: String, email: String, password: String, confirmPassword: String) =
        AuthResult.Success(AuthUser("1", email, username))
    override suspend fun login(email: String, password: String) = AuthResult.Success(AuthUser("1", email, "User"))
    override suspend fun restoreSession() = state.value
    override suspend fun logout() { mutableState.value = AuthState.Guest }
    override suspend fun invalidateSession() { mutableState.value = AuthState.Guest }
    override fun requireAuthentication(action: PendingAuthAction) = state.value is AuthState.Authenticated
    override fun clearPendingAction() { pendingAction.value = null }
    override fun currentUser() = (state.value as? AuthState.Authenticated)?.user
    override fun currentToken(): String? = null
}

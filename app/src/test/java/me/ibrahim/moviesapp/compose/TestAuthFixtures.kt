package me.ibrahim.moviesapp.compose

import me.ibrahim.moviesapp.compose.domain.auth.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAuthRepository : AuthRepository {
    private val mutableState = MutableStateFlow<AuthState>(AuthState.Guest)
    override val state: StateFlow<AuthState> = mutableState
    private val mutablePending = MutableStateFlow<PendingAuthAction?>(null)
    override val pendingAction: StateFlow<PendingAuthAction?> = mutablePending
    var nextResult: AuthResult<AuthUser> = AuthResult.Success(USER)
    var restoreResult: AuthState = AuthState.Guest
    var loginCalls = 0
    var registerCalls = 0
    var logoutCalls = 0

    override suspend fun register(username: String, email: String, password: String,
                                  confirmPassword: String): AuthResult<AuthUser> {
        registerCalls++
        if (nextResult is AuthResult.Success) mutableState.value = AuthState.Authenticated((nextResult as AuthResult.Success).value)
        return nextResult
    }
    override suspend fun login(email: String, password: String): AuthResult<AuthUser> {
        loginCalls++
        if (nextResult is AuthResult.Success) mutableState.value = AuthState.Authenticated((nextResult as AuthResult.Success).value)
        return nextResult
    }
    override suspend fun restoreSession(): AuthState { mutableState.value = restoreResult; return restoreResult }
    override suspend fun logout() { logoutCalls++; mutableState.value = AuthState.Guest }
    override suspend fun invalidateSession() { mutableState.value = AuthState.Guest }
    override fun requireAuthentication(action: PendingAuthAction): Boolean {
        if (mutableState.value is AuthState.Authenticated) return true
        mutablePending.value = action
        return false
    }
    override fun clearPendingAction() { mutablePending.value = null }
    override fun currentUser() = (mutableState.value as? AuthState.Authenticated)?.user
    override fun currentToken(): String? = null
    fun setState(value: AuthState) { mutableState.value = value }

    companion object { val USER = AuthUser("user-1", "alice@example.com", "Alice") }
}

package me.ibrahim.moviesapp.compose.domain.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val state: StateFlow<AuthState>
    val pendingAction: StateFlow<PendingAuthAction?>

    suspend fun register(username: String, email: String, password: String,
                         confirmPassword: String): AuthResult<AuthUser>
    suspend fun login(email: String, password: String): AuthResult<AuthUser>
    suspend fun restoreSession(): AuthState
    suspend fun logout()
    suspend fun invalidateSession()
    fun requireAuthentication(action: PendingAuthAction): Boolean
    fun clearPendingAction()
    fun currentUser(): AuthUser?
    fun currentToken(): String?
}

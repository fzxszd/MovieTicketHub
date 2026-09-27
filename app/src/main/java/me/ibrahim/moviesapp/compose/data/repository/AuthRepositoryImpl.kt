package me.ibrahim.moviesapp.compose.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.ibrahim.moviesapp.compose.data.auth.SessionStore
import me.ibrahim.moviesapp.compose.data.dto.AuthSuccessDto
import me.ibrahim.moviesapp.compose.data.dto.AuthUserDto
import me.ibrahim.moviesapp.compose.data.network.AuthRemoteApi
import me.ibrahim.moviesapp.compose.domain.auth.AuthError
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository
import me.ibrahim.moviesapp.compose.domain.auth.AuthResult
import me.ibrahim.moviesapp.compose.domain.auth.AuthState
import me.ibrahim.moviesapp.compose.domain.auth.AuthUser
import me.ibrahim.moviesapp.compose.domain.auth.PendingAuthAction

class AuthRepositoryImpl(
    private val remote: AuthRemoteApi,
    private val sessionStore: SessionStore,
    private val userAccountCache: UserAccountCache
) : AuthRepository {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<AuthState>(AuthState.Checking)
    override val state: StateFlow<AuthState> = _state.asStateFlow()
    private val _pendingAction = MutableStateFlow<PendingAuthAction?>(null)
    override val pendingAction: StateFlow<PendingAuthAction?> = _pendingAction.asStateFlow()
    @Volatile private var token: String? = null

    override suspend fun register(username: String, email: String, password: String,
                                  confirmPassword: String): AuthResult<AuthUser> = mutex.withLock {
        handleSessionResult(remote.register(username, email, password, confirmPassword))
    }

    override suspend fun login(email: String, password: String): AuthResult<AuthUser> = mutex.withLock {
        handleSessionResult(remote.login(email, password))
    }

    override suspend fun restoreSession(): AuthState = mutex.withLock {
        _state.value = AuthState.Checking
        retryPendingRevocation()
        val stored = sessionStore.read()
        if (stored == null) {
            token = null
            _state.value = AuthState.Guest
            return@withLock _state.value
        }
        token = stored.token
        when (val result = remote.me(stored.token)) {
            is AuthResult.Success -> {
                val user = result.value.toDomain()
                cacheUser(user)
                _state.value = AuthState.Authenticated(user)
            }
            is AuthResult.Failure -> when (result.error) {
                AuthError.Unauthenticated -> {
                    sessionStore.clear(); token = null; _state.value = AuthState.Guest
                }
                else -> _state.value = AuthState.RecoverableError(result.error)
            }
        }
        _state.value
    }

    override suspend fun logout() = mutex.withLock {
        val active = token ?: sessionStore.read()?.token
        if (active != null) {
            when (remote.logout(active)) {
                is AuthResult.Success -> sessionStore.clear()
                is AuthResult.Failure -> sessionStore.moveCurrentToPendingRevocation()
            }
        } else sessionStore.clear()
        token = null
        _pendingAction.value = null
        _state.value = AuthState.Guest
    }

    override suspend fun invalidateSession() = mutex.withLock {
        sessionStore.clear()
        token = null
        _pendingAction.value = null
        _state.value = AuthState.Guest
    }

    override fun requireAuthentication(action: PendingAuthAction): Boolean {
        if (_state.value is AuthState.Authenticated) return true
        _pendingAction.value = action
        return false
    }

    override fun clearPendingAction() { _pendingAction.value = null }
    override fun currentUser(): AuthUser? = (_state.value as? AuthState.Authenticated)?.user
    override fun currentToken(): String? = token

    private suspend fun handleSessionResult(result: AuthResult<AuthSuccessDto>): AuthResult<AuthUser> {
        return when (result) {
            is AuthResult.Success -> {
                val user = result.value.user.toDomain()
                sessionStore.save(result.value.token, result.value.expiresAt, user.id)
                token = result.value.token
                cacheUser(user)
                _state.value = AuthState.Authenticated(user)
                AuthResult.Success(user)
            }
            is AuthResult.Failure -> result
        }
    }

    private suspend fun cacheUser(user: AuthUser) {
        userAccountCache.save(user)
    }

    private suspend fun retryPendingRevocation() {
        val pending = sessionStore.readPendingRevocation() ?: return
        if (remote.logout(pending) is AuthResult.Success) sessionStore.clearPendingRevocation()
    }

    private fun AuthUserDto.toDomain() = AuthUser(id, email, username, avatarUri)
}

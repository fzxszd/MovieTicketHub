package me.ibrahim.moviesapp.compose.data.repository

import kotlinx.coroutines.test.runTest
import me.ibrahim.moviesapp.compose.data.auth.SessionStore
import me.ibrahim.moviesapp.compose.data.auth.StoredSession
import me.ibrahim.moviesapp.compose.data.dto.AuthSuccessDto
import me.ibrahim.moviesapp.compose.data.dto.AuthUserDto
import me.ibrahim.moviesapp.compose.data.network.AuthRemoteApi
import me.ibrahim.moviesapp.compose.domain.auth.*
import org.junit.Assert.*
import org.junit.Test

class AuthRepositoryImplTest {
    @Test fun loginPersistsSessionAndPublishesAuthenticatedState() = runTest {
        val remote = FakeRemote()
        val store = FakeStore()
        val cache = FakeCache()
        val repository = AuthRepositoryImpl(remote, store, cache)
        val result = repository.login("alice@example.com", "password123")
        assertTrue(result is AuthResult.Success)
        assertEquals("opaque", store.session?.token)
        assertEquals("user-1", cache.user?.id)
        assertTrue(repository.state.value is AuthState.Authenticated)
    }

    @Test fun revokedSessionIsClearedWhileNetworkFailureIsRecoverable() = runTest {
        val remote = FakeRemote()
        val store = FakeStore().apply { session = StoredSession("opaque", Long.MAX_VALUE, "user-1") }
        val repository = AuthRepositoryImpl(remote, store, FakeCache())
        remote.meResult = AuthResult.Failure(AuthError.Unauthenticated)
        assertEquals(AuthState.Guest, repository.restoreSession())
        assertNull(store.session)
        store.session = StoredSession("opaque", Long.MAX_VALUE, "user-1")
        remote.meResult = AuthResult.Failure(AuthError.Network)
        assertEquals(AuthState.RecoverableError(AuthError.Network), repository.restoreSession())
        assertNotNull(store.session)
    }

    @Test fun offlineLogoutMovesTokenOutOfActiveSession() = runTest {
        val remote = FakeRemote()
        val store = FakeStore()
        val repository = AuthRepositoryImpl(remote, store, FakeCache())
        repository.login("alice@example.com", "password123")
        remote.logoutResult = AuthResult.Failure(AuthError.Network)
        repository.logout()
        assertNull(store.session)
        assertEquals("opaque", store.pending)
        assertEquals(AuthState.Guest, repository.state.value)
    }
}

private class FakeRemote : AuthRemoteApi {
    private val user = AuthUserDto("user-1", "Alice", "alice@example.com")
    private val success = AuthSuccessDto(user, "opaque", "2099-01-01T00:00:00+00:00")
    var meResult: AuthResult<AuthUserDto> = AuthResult.Success(user)
    var logoutResult: AuthResult<Unit> = AuthResult.Success(Unit)
    override suspend fun register(username: String, email: String, password: String, confirmPassword: String) = AuthResult.Success(success)
    override suspend fun login(email: String, password: String) = AuthResult.Success(success)
    override suspend fun me(token: String) = meResult
    override suspend fun logout(token: String) = logoutResult
}

private class FakeStore : SessionStore {
    var session: StoredSession? = null
    var pending: String? = null
    override fun save(token: String, expiresAt: String, userIdHint: String?) { session = StoredSession(token, Long.MAX_VALUE, userIdHint) }
    override fun read() = session
    override fun clear() { session = null }
    override fun moveCurrentToPendingRevocation() { pending = session?.token; session = null }
    override fun readPendingRevocation() = pending
    override fun clearPendingRevocation() { pending = null }
}

private class FakeCache : UserAccountCache {
    var user: AuthUser? = null
    override suspend fun save(user: AuthUser) { this.user = user }
}

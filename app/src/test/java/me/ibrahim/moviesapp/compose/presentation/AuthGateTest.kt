package me.ibrahim.moviesapp.compose.presentation

import me.ibrahim.moviesapp.compose.FakeAuthRepository
import me.ibrahim.moviesapp.compose.domain.auth.AuthState
import me.ibrahim.moviesapp.compose.domain.auth.PendingAuthAction
import org.junit.Assert.*
import org.junit.Test

class AuthGateTest {
    @Test fun guestActionsAreRecordedWithoutSideEffects() {
        val repository = FakeAuthRepository()
        val actions = listOf(
            PendingAuthAction.FavoriteMovie(1), PendingAuthAction.ViewRecommendations("home"),
            PendingAuthAction.SelectSeats(1, "cinema", "time"), PendingAuthAction.Purchase("draft")
        )
        actions.forEach { action ->
            assertFalse(repository.requireAuthentication(action))
            assertEquals(action, repository.pendingAction.value)
            repository.clearPendingAction()
        }
    }
    @Test fun authenticatedActionIsAllowedButNotAutomaticallyExecuted() {
        val repository = FakeAuthRepository().apply { restoreResult = AuthState.Authenticated(FakeAuthRepository.USER) }
        kotlinx.coroutines.runBlocking { repository.restoreSession() }
        assertTrue(repository.requireAuthentication(PendingAuthAction.FavoriteMovie(1)))
        assertNull(repository.pendingAction.value)
    }
}

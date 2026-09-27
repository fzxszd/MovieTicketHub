package me.ibrahim.moviesapp.compose.presentation.splash

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.ibrahim.moviesapp.compose.FakeAuthRepository
import me.ibrahim.moviesapp.compose.domain.auth.AuthError
import me.ibrahim.moviesapp.compose.domain.auth.AuthState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()
    @Test fun guestAndAuthenticatedSessionsEnterMain() {
        val repository = FakeAuthRepository()
        SplashViewModel(repository).apply { restore(); assertEquals(SplashRoute.Main, route.value) }
        repository.restoreResult = AuthState.Authenticated(FakeAuthRepository.USER)
        SplashViewModel(repository).apply { restore(); assertEquals(SplashRoute.Main, route.value) }
    }
    @Test fun temporaryNetworkFailureOffersRetry() {
        val repository = FakeAuthRepository().apply { restoreResult = AuthState.RecoverableError(AuthError.Network) }
        SplashViewModel(repository).apply { restore(); assertEquals(SplashRoute.Retry, route.value) }
    }
}

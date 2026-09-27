package me.ibrahim.moviesapp.compose.presentation.login

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.ibrahim.moviesapp.compose.FakeAuthRepository
import me.ibrahim.moviesapp.compose.domain.auth.AuthError
import me.ibrahim.moviesapp.compose.domain.auth.AuthResult
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun invalidRegistrationIsRejectedBeforeRepositoryCall() {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)
        viewModel.toggleMode()
        viewModel.updateUsername("x")
        viewModel.updateEmail("invalid")
        viewModel.updatePassword("short")
        viewModel.updateConfirmation("different")
        viewModel.submit()
        assertEquals(0, repository.registerCalls)
        assertEquals(4, viewModel.state.value.fieldErrors.size)
    }

    @Test fun validRegistrationAuthenticatesAndClearsPassword() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)
        viewModel.toggleMode(); viewModel.updateUsername("Alice")
        viewModel.updateEmail("alice@example.com"); viewModel.updatePassword("password123")
        viewModel.updateConfirmation("password123"); viewModel.submit()
        assertEquals(1, repository.registerCalls)
        assertEquals("", viewModel.state.value.password)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test fun credentialAndRateLimitErrorsAreDistinct() {
        val repository = FakeAuthRepository().apply { nextResult = AuthResult.Failure(AuthError.InvalidCredentials) }
        val viewModel = LoginViewModel(repository)
        viewModel.updateEmail("alice@example.com"); viewModel.updatePassword("bad")
        viewModel.submit()
        assertEquals(LoginMessage.INVALID_CREDENTIALS, viewModel.state.value.message)
        repository.nextResult = AuthResult.Failure(AuthError.RateLimited(42))
        viewModel.submit()
        assertEquals(LoginMessage.RATE_LIMITED, viewModel.state.value.message)
        assertEquals(42, viewModel.state.value.retryAfterSeconds)
    }
}

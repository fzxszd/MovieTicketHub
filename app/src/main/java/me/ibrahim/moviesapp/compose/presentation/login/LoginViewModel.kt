package me.ibrahim.moviesapp.compose.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ibrahim.moviesapp.compose.domain.auth.AuthError
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository
import me.ibrahim.moviesapp.compose.domain.auth.AuthResult
import me.ibrahim.moviesapp.compose.R

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()
    private val eventChannel = Channel<LoginEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun toggleMode() = _state.update {
        it.copy(mode = if (it.mode == LoginMode.LOGIN) LoginMode.REGISTER else LoginMode.LOGIN,
            password = "", confirmPassword = "", fieldErrors = emptyMap(), message = null)
    }
    fun updateUsername(value: String) = update { copy(username = value, fieldErrors = fieldErrors - LoginField.USERNAME) }
    fun updateEmail(value: String) = update { copy(email = value, fieldErrors = fieldErrors - LoginField.EMAIL) }
    fun updatePassword(value: String) = update { copy(password = value, fieldErrors = fieldErrors - LoginField.PASSWORD) }
    fun updateConfirmation(value: String) = update { copy(confirmPassword = value, fieldErrors = fieldErrors - LoginField.CONFIRM_PASSWORD) }

    fun submit() {
        val snapshot = _state.value
        if (snapshot.isSubmitting) return
        val errors = validate(snapshot)
        if (errors.isNotEmpty()) { _state.update { it.copy(fieldErrors = errors) }; return }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, message = null) }
            val result = if (snapshot.mode == LoginMode.REGISTER) {
                authRepository.register(snapshot.username.trim(), snapshot.email.trim(),
                    snapshot.password, snapshot.confirmPassword)
            } else authRepository.login(snapshot.email.trim(), snapshot.password)
            when (result) {
                is AuthResult.Success -> {
                    _state.update { it.copy(isSubmitting = false, password = "", confirmPassword = "") }
                    eventChannel.send(LoginEvent.Authenticated)
                }
                is AuthResult.Failure -> showError(result.error)
            }
        }
    }

    private fun validate(state: LoginUiState): Map<LoginField, Int> = buildMap {
        if (state.mode == LoginMode.REGISTER && state.username.trim().length !in 2..30)
            put(LoginField.USERNAME, R.string.auth_username_error)
        if (!EMAIL.matches(state.email.trim())) put(LoginField.EMAIL, R.string.auth_email_error)
        val minimum = if (state.mode == LoginMode.REGISTER) 8 else 1
        if (state.password.length !in minimum..64)
            put(LoginField.PASSWORD, if (minimum == 8) R.string.auth_password_error else R.string.auth_password_required)
        if (state.mode == LoginMode.REGISTER && state.confirmPassword != state.password)
            put(LoginField.CONFIRM_PASSWORD, R.string.auth_confirm_error)
    }

    private fun showError(error: AuthError) = _state.update { old ->
        when (error) {
            is AuthError.Validation -> old.copy(isSubmitting = false,
                fieldErrors = error.fieldErrors.mapNotNull { (key, _) ->
                    runCatching { LoginField.valueOf(key.uppercase()) }.getOrNull()?.let { field ->
                        field to when (field) {
                            LoginField.USERNAME -> R.string.auth_username_error
                            LoginField.EMAIL -> R.string.auth_email_error
                            LoginField.PASSWORD -> R.string.auth_password_error
                            LoginField.CONFIRM_PASSWORD -> R.string.auth_confirm_error
                        }
                    }
                }.toMap())
            AuthError.EmailExists -> old.copy(isSubmitting = false, message = LoginMessage.EMAIL_EXISTS)
            AuthError.InvalidCredentials -> old.copy(isSubmitting = false, message = LoginMessage.INVALID_CREDENTIALS)
            AuthError.Network -> old.copy(isSubmitting = false, message = LoginMessage.NETWORK)
            is AuthError.RateLimited -> old.copy(isSubmitting = false, message = LoginMessage.RATE_LIMITED,
                retryAfterSeconds = error.retryAfterSeconds)
            else -> old.copy(isSubmitting = false, message = LoginMessage.SERVICE)
        }
    }

    private fun update(block: LoginUiState.() -> LoginUiState) = _state.update(block)

    private companion object { val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") }
}

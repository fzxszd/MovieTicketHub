package me.ibrahim.moviesapp.compose.presentation.login

enum class LoginMode { LOGIN, REGISTER }
enum class LoginField { USERNAME, EMAIL, PASSWORD, CONFIRM_PASSWORD }
enum class LoginMessage { INVALID_CREDENTIALS, EMAIL_EXISTS, NETWORK, RATE_LIMITED, SERVICE }

data class LoginUiState(
    val mode: LoginMode = LoginMode.LOGIN,
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fieldErrors: Map<LoginField, Int> = emptyMap(),
    val isSubmitting: Boolean = false,
    val message: LoginMessage? = null,
    val retryAfterSeconds: Int? = null
)

sealed interface LoginEvent { data object Authenticated : LoginEvent }

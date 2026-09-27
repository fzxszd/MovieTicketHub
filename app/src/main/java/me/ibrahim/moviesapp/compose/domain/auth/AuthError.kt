package me.ibrahim.moviesapp.compose.domain.auth

sealed interface AuthError {
    data class Validation(val fieldErrors: Map<String, String>) : AuthError
    data object EmailExists : AuthError
    data object InvalidCredentials : AuthError
    data object Network : AuthError
    data class RateLimited(val retryAfterSeconds: Int) : AuthError
    data object Unauthenticated : AuthError
    data object Service : AuthError
}

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class Failure(val error: AuthError) : AuthResult<Nothing>
}

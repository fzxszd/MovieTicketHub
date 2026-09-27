package me.ibrahim.moviesapp.compose.domain.auth

sealed interface AuthState {
    data object Checking : AuthState
    data object Guest : AuthState
    data class Authenticated(val user: AuthUser) : AuthState
    data class RecoverableError(val error: AuthError) : AuthState
}

sealed interface PendingAuthAction {
    data class FavoriteMovie(val movieId: Int) : PendingAuthAction
    data class ViewRecommendations(val sourceRoute: String) : PendingAuthAction
    data class SelectSeats(val movieId: Int, val cinema: String, val showtime: String) : PendingAuthAction
    data class Purchase(val orderDraftId: String) : PendingAuthAction
}

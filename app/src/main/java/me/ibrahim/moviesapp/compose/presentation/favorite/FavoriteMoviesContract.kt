package me.ibrahim.moviesapp.compose.presentation.favorite

sealed interface FavoriteMoviesContract {
    data object Loading: FavoriteMoviesContract
    data class Content(val count:Int): FavoriteMoviesContract
    data object Empty: FavoriteMoviesContract
    data class Offline(val pending:Int): FavoriteMoviesContract
    data class Error(val code:String): FavoriteMoviesContract
}

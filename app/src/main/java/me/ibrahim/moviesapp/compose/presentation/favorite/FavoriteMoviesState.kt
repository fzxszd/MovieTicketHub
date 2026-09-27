package me.ibrahim.moviesapp.compose.presentation.favorite

import me.ibrahim.moviesapp.compose.domain.Movie

data class FavoriteMoviesState(
    val favoriteMovies: List<Movie> = emptyList(),
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null
)

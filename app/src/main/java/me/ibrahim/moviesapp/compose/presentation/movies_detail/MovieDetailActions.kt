package me.ibrahim.moviesapp.compose.presentation.movies_detail

import me.ibrahim.moviesapp.compose.domain.Movie

sealed interface MovieDetailActions {
    data object GoBack : MovieDetailActions
    data class MarkFavorite(val movie: Movie) : MovieDetailActions
    data class OnMovieClick(val movie: Movie) : MovieDetailActions
    data class RateMovie(val rating: Int) : MovieDetailActions
    data class SubmitComment(val content: String, val parentId: Int? = null) : MovieDetailActions
    data class ToggleLike(val commentId: Int) : MovieDetailActions
}
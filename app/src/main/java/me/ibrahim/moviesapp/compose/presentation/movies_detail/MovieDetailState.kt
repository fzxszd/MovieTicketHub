package me.ibrahim.moviesapp.compose.presentation.movies_detail

import me.ibrahim.moviesapp.compose.BuildConfig
import me.ibrahim.moviesapp.compose.data.database.CommentEntity
import me.ibrahim.moviesapp.compose.domain.Actor
import me.ibrahim.moviesapp.compose.domain.Movie
import me.ibrahim.moviesapp.compose.presentation.main.UiText
import me.ibrahim.moviesapp.compose.domain.favorite.FavoriteSyncStatus

data class MovieDetailState(
    val actors: List<Actor> = emptyList(),
    val movie: Movie = Movie(
        title = "",
        id = 0,
        posterPath = "",
        overview = ""
    ),
    val isFavorite: Boolean = false,
    val favoriteStatus: FavoriteSyncStatus = FavoriteSyncStatus.CONFIRMED,
    val isLoading: Boolean = false,
    val userRating: Int = 0,
    val hasPurchased: Boolean = false,
    val comments: List<CommentEntity> = emptyList(),
    val likedCommentIds: Set<Int> = emptySet(), // 记录点赞过的评论
    val authRequired: Boolean = false,
    val errorMessage: UiText? = null
)

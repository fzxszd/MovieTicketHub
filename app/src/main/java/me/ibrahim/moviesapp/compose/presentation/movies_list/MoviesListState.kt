package me.ibrahim.moviesapp.compose.presentation.movies_list

import me.ibrahim.moviesapp.compose.domain.Movie
import me.ibrahim.moviesapp.compose.presentation.main.UiText

data class MoviesListState(
    val isLoading: Boolean = false,
    val isRecLoading: Boolean = false, // 推荐模块专属加载状态
    val nowPlayingMovies: List<Movie> = emptyList(),
    val upcomingMovies: List<Movie> = emptyList(),
    val recommendedMovies: List<Movie> = emptyList(),
    val isPersonalized: Boolean = false,
    val filteredNowPlayingMovies: List<Movie> = emptyList(),
    val filteredUpcomingMovies: List<Movie> = emptyList(),
    val searchQuery: String = "",
    val errorMsg: UiText? = null,
    
    // 推荐系统权重
    val weightPreference: Float = 0.5f, // 我的喜好
    val weightPopularity: Float = 0.3f, // 全网热度
    val weightFreshness: Float = 0.2f   // 上映时间
)

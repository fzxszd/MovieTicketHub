package me.ibrahim.moviesapp.compose.data.network

import me.ibrahim.moviesapp.compose.data.dto.MaoyanComingResponseDto
import me.ibrahim.moviesapp.compose.data.dto.MaoyanDetailResponseDto
import me.ibrahim.moviesapp.compose.data.dto.MaoyanMovieOnInfoResponseDto
import me.ibrahim.moviesapp.compose.domain.DataError
import me.ibrahim.moviesapp.compose.domain.Result

interface MoviesRemoteApi {

    suspend fun fetchNowPlayingMovies(): Result<MaoyanMovieOnInfoResponseDto, DataError.Remote>
    suspend fun fetchUpcomingMovies(): Result<MaoyanComingResponseDto, DataError.Remote>
    suspend fun fetchMovieDetail(movieId: Int): Result<MaoyanDetailResponseDto, DataError.Remote>
}

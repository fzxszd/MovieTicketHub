package me.ibrahim.moviesapp.compose.data.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import me.ibrahim.moviesapp.compose.data.dto.MaoyanComingResponseDto
import me.ibrahim.moviesapp.compose.data.dto.MaoyanDetailResponseDto
import me.ibrahim.moviesapp.compose.data.dto.MaoyanMovieOnInfoResponseDto
import me.ibrahim.moviesapp.compose.domain.DataError
import me.ibrahim.moviesapp.compose.domain.Result

class MoviesRemoteApiImpl(private val httpClient: HttpClient) : MoviesRemoteApi {

    override suspend fun fetchNowPlayingMovies(): Result<MaoyanMovieOnInfoResponseDto, DataError.Remote> {
        return safeCall {
            httpClient.get("${RemoteApiEndpoints.BASE_URL}${RemoteApiEndpoints.MOVIE_ON_INFO_LIST}")
        }
    }

    override suspend fun fetchUpcomingMovies(): Result<MaoyanComingResponseDto, DataError.Remote> {
        return safeCall {
            httpClient.get("${RemoteApiEndpoints.BASE_URL}${RemoteApiEndpoints.COMING_LIST}")
        }
    }

    override suspend fun fetchMovieDetail(movieId: Int): Result<MaoyanDetailResponseDto, DataError.Remote> {
        return safeCall {
            httpClient.get("${RemoteApiEndpoints.BASE_URL}${RemoteApiEndpoints.MOVIE_DETAIL}") {
                url {
                    parameters.append("movieId", movieId.toString())
                }
            }
        }
    }
}

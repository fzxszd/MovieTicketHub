package me.ibrahim.moviesapp.compose.domain.cinema

sealed interface CinemaError { data object Network: CinemaError; data object Timeout: CinemaError; data object InvalidData: CinemaError; data class Service(val code:String): CinemaError }
sealed interface CinemaResult<out T> { data class Success<T>(val value:T):CinemaResult<T>; data class Failure(val error:CinemaError):CinemaResult<Nothing> }

interface CinemaRepository {
    suspend fun availableDates(movieId: Int, cityCode: String): CinemaResult<List<String>>
    suspend fun showtimes(query: ShowtimeQuery): CinemaResult<CinemaShowtimeResult>
    suspend fun validate(showtimeId: String, observedVersion: Long): CinemaResult<ShowtimeValidation>
}

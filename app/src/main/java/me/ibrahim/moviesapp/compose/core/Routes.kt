package me.ibrahim.moviesapp.compose.core

import kotlinx.serialization.Serializable
import me.ibrahim.moviesapp.compose.domain.Movie

@Serializable
object MoviesListRoute

@Serializable
data class MovieDetailRoute(val movie: Movie)

@Serializable
object FavoriteMoviesRoute

@Serializable
object SearchMoviesRoute

@Serializable
object SettingsRoute
@Serializable object OrdersRoute
@Serializable data class OrderDetailRoute(val orderId:String)

@Serializable
data class CinemaSelectionRoute(val movieId: Int, val movieTitle: String)

@Serializable
data class TimeSelectionRoute(val movieId: Int, val cinemaId: String, val localDate: String)

@Serializable
data class SeatSelectionRoute(
    val movieId: Int,
    val cinemaId: String,
    val auditoriumId: String,
    val showtimeId: String,
    val latestVersion: Long,
    val priceMinor: Long
)

@Serializable
data class PaymentRoute(
    val lockId: String = "",
    val orderId: String? = null
)

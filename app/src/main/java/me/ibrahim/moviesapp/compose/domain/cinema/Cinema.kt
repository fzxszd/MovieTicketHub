package me.ibrahim.moviesapp.compose.domain.cinema

data class Cinema(
    val id: String, val name: String, val address: String, val cityCode: String,
    val district: String, val timeZone: String, val distanceMeters: Int?,
    val minimumPrice: Money, val showtimes: List<Showtime>
)

data class CinemaShowtimeResult(
    val movieId: Int, val localDate: String, val cityCode: String, val serverTime: String,
    val requestedSort: SortType, val appliedSort: SortType, val sortNotice: SortNotice?,
    val availableDistricts: List<String>, val cinemas: List<Cinema>
)

enum class SortNotice { LOCATION_UNAVAILABLE }

package me.ibrahim.moviesapp.compose.domain.cinema

enum class SortType(val wireName: String) { RECOMMENDED("recommended"), DISTANCE("distance"), PRICE("price") }

data class ShowtimeQuery(
    val movieId: Int, val localDate: String, val cityCode: String = "CN-SH",
    val districts: Set<String> = emptySet(), val startLocalTime: String? = null,
    val endLocalTime: String? = null, val minPriceMinor: Long? = null,
    val maxPriceMinor: Long? = null, val sort: SortType = SortType.RECOMMENDED,
    val latitude: Double? = null, val longitude: Double? = null
) {
    fun validationError(): String? = when {
        movieId <= 0 -> "movieId"
        !localDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> "localDate"
        cityCode.isBlank() -> "cityCode"
        minPriceMinor != null && minPriceMinor < 0 -> "minPriceMinor"
        maxPriceMinor != null && maxPriceMinor < 0 -> "maxPriceMinor"
        minPriceMinor != null && maxPriceMinor != null && maxPriceMinor < minPriceMinor -> "priceRange"
        (latitude == null) != (longitude == null) -> "location"
        latitude != null && latitude !in -90.0..90.0 -> "latitude"
        longitude != null && longitude !in -180.0..180.0 -> "longitude"
        startLocalTime != null && !startLocalTime.matches(TIME) -> "startLocalTime"
        endLocalTime != null && !endLocalTime.matches(TIME) -> "endLocalTime"
        startLocalTime != null && endLocalTime != null && endLocalTime <= startLocalTime -> "timeRange"
        else -> null
    }
    private companion object { val TIME = Regex("([01]\\d|2[0-3]):[0-5]\\d") }
}

package me.ibrahim.moviesapp.compose.domain.cinema

enum class ShowtimeStatus { ON_SALE, STOPPED, SOLD_OUT, CANCELLED }
enum class UnavailableReason { STARTED, ENDED, CANCELLED, SALES_STOPPED, SOLD_OUT, CINEMA_CLOSED, AUDITORIUM_INACTIVE }
enum class ValidationResult { UNCHANGED, CHANGED, UNAVAILABLE }
enum class ChangedField { PRICE, START_TIME, END_TIME, AUDITORIUM, STATUS }

data class Showtime(
    val id: String, val movieId: Int, val cinemaId: String, val auditoriumId: String,
    val auditoriumName: String, val startsAt: String, val endsAt: String, val timeZone: String,
    val language: String, val format: String, val basePrice: Money,
    val status: ShowtimeStatus, val selectable: Boolean,
    val unavailableReason: UnavailableReason?, val version: Long
)

data class ShowtimeValidation(
    val result: ValidationResult, val serverTime: String, val changedFields: Set<ChangedField>,
    val messageCode: String, val latest: Showtime
)

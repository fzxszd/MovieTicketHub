package me.ibrahim.moviesapp.compose.data.mappers

import me.ibrahim.moviesapp.compose.data.dto.*
import me.ibrahim.moviesapp.compose.domain.cinema.*

fun MoneyDto.toDomain(): Money = Money(amountMinor, currency)

fun ShowtimeDto.toDomain(): Showtime {
    require(id.isNotBlank() && movieId > 0 && cinemaId.isNotBlank() && auditoriumId.isNotBlank())
    require(startsAt.contains('T') && endsAt.contains('T') && timeZone.contains('/'))
    val status = enumValueOf<ShowtimeStatus>(salesStatus)
    val reason = unavailableReason?.let { enumValueOf<UnavailableReason>(it) }
    require(selectable == (status == ShowtimeStatus.ON_SALE && reason == null))
    return Showtime(id,movieId,cinemaId,auditoriumId,auditoriumName,startsAt,endsAt,timeZone,
        language,format,basePrice.toDomain(),status,selectable,reason,version.also { require(it>=1) })
}

fun CinemaShowtimeResponseDto.toDomain(query: ShowtimeQuery): CinemaShowtimeResult {
    require(movieId == query.movieId && localDate == query.localDate && cityCode == query.cityCode)
    val cinemaModels = cinemas.map { dto ->
        val shows = dto.showtimes.map { it.toDomain() }
        require(shows.isNotEmpty() && shows.all { it.cinemaId == dto.id && it.selectable })
        Cinema(dto.id,dto.name,dto.address,dto.cityCode,dto.district,dto.timeZone,
            dto.distanceMeters,dto.minimumPrice.toDomain(),shows)
    }
    return CinemaShowtimeResult(movieId,localDate,cityCode,serverTime,
        SortType.entries.first { it.wireName == requestedSort },
        SortType.entries.first { it.wireName == appliedSort },
        sortNotice?.let { enumValueOf<SortNotice>(it) },availableDistricts,cinemaModels)
}

fun ShowtimeValidationDto.toDomain() = ShowtimeValidation(
    enumValueOf(result),serverTime,changedFields.map { enumValueOf<ChangedField>(it) }.toSet(),
    messageCode,latest.toDomain())

package me.ibrahim.moviesapp.compose.data.dto

import kotlinx.serialization.Serializable

@Serializable data class ShowtimeDatesDto(val movieId:Int,val cityCode:String,val serverTime:String,val dates:List<String>)
@Serializable data class MoneyDto(val amountMinor:Long,val currency:String)
@Serializable data class ShowtimeDto(
    val id:String,val movieId:Int,val cinemaId:String,val auditoriumId:String,val auditoriumName:String,
    val startsAt:String,val endsAt:String,val timeZone:String,val language:String,val format:String,
    val basePrice:MoneyDto,val salesStatus:String,val selectable:Boolean,
    val unavailableReason:String?=null,val version:Long)
@Serializable data class CinemaDto(
    val id:String,val name:String,val address:String,val cityCode:String,val district:String,val timeZone:String,
    val distanceMeters:Int?=null,val minimumPrice:MoneyDto,val showtimes:List<ShowtimeDto>)
@Serializable data class CinemaShowtimeResponseDto(
    val movieId:Int,val localDate:String,val cityCode:String,val serverTime:String,
    val requestedSort:String,val appliedSort:String,val sortNotice:String?=null,
    val availableDistricts:List<String>,val cinemas:List<CinemaDto>)
@Serializable data class ShowtimeValidationDto(
    val result:String,val serverTime:String,val changedFields:Set<String>,val messageCode:String,val latest:ShowtimeDto)
@Serializable data class CinemaApiErrorDto(val code:String,val message:String,val field:String?=null)

package me.ibrahim.moviesapp.compose.domain.seat

import me.ibrahim.moviesapp.compose.domain.cinema.Money
enum class SeatLockStatus { ACTIVE, RELEASED, EXPIRED, CONVERTED, INVALIDATED }
data class LockedSeat(val seatId:String,val rowLabel:String,val seatLabel:String,val priceZoneId:String,val unitPrice:Money)
data class SeatLock(val id:String,val showtimeId:String,val status:SeatLockStatus,val serverTime:String,val createdAt:String,val expiresAt:String,val items:List<LockedSeat>,val totalPrice:Money,val releaseReason:String?)
sealed interface SeatResult<out T>{data class Success<T>(val value:T):SeatResult<T>;data class Failure(val code:String):SeatResult<Nothing>}

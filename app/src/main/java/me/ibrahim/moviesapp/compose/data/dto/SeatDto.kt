package me.ibrahim.moviesapp.compose.data.dto
import kotlinx.serialization.Serializable
@Serializable data class SeatPositionDto(val seatId:String?=null,val rowIndex:Int,val columnIndex:Int,val rowLabel:String,val seatLabel:String?=null,val positionType:String,val priceZoneId:String?=null,val price:MoneyDto?=null,val inventoryStatus:String?=null,val unavailableReason:String?=null)
@Serializable data class SeatRowDto(val rowIndex:Int,val rowLabel:String,val positions:List<SeatPositionDto>)
@Serializable data class SeatLayoutDto(val showtimeId:String,val auditoriumId:String,val auditoriumName:String,val screenLabel:String,val serverTime:String,val inventoryVersion:Long,val rows:List<SeatRowDto>)
@Serializable data class LockRequestDto(val seatIds:List<String>,val observedInventoryVersion:Long)
@Serializable data class LockedSeatDto(val seatId:String,val rowLabel:String,val seatLabel:String,val priceZoneId:String,val unitPrice:MoneyDto)
@Serializable data class SeatLockDto(val id:String,val showtimeId:String,val status:String,val serverTime:String,val createdAt:String,val expiresAt:String,val releaseReason:String?=null,val items:List<LockedSeatDto>,val totalPrice:MoneyDto)

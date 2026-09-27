package me.ibrahim.moviesapp.compose.domain.seat

import me.ibrahim.moviesapp.compose.domain.cinema.Money

enum class SeatPositionType { SEAT, AISLE, EMPTY, COUPLE_LEFT, COUPLE_RIGHT }
enum class SeatInventoryStatus { AVAILABLE, LOCKED_BY_ME, LOCKED_BY_OTHER, SOLD, UNAVAILABLE }
data class SeatPosition(val seatId:String?,val rowIndex:Int,val columnIndex:Int,val rowLabel:String,val seatLabel:String?,val positionType:SeatPositionType,val priceZoneId:String?,val price:Money?,val inventoryStatus:SeatInventoryStatus?,val unavailableReason:String?)
data class SeatRow(val rowIndex:Int,val rowLabel:String,val positions:List<SeatPosition>)
data class SeatLayoutSnapshot(val showtimeId:String,val auditoriumId:String,val auditoriumName:String,val screenLabel:String,val serverTime:String,val inventoryVersion:Long,val rows:List<SeatRow>)

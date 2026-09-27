package me.ibrahim.moviesapp.compose.data.mappers
import me.ibrahim.moviesapp.compose.data.dto.*
import me.ibrahim.moviesapp.compose.domain.seat.*
fun SeatLayoutDto.toDomain()=SeatLayoutSnapshot(showtimeId,auditoriumId,auditoriumName,screenLabel,serverTime,inventoryVersion,rows.map{r->SeatRow(r.rowIndex,r.rowLabel,r.positions.map{p->SeatPosition(p.seatId,p.rowIndex,p.columnIndex,p.rowLabel,p.seatLabel,enumValueOf<SeatPositionType>(p.positionType),p.priceZoneId,p.price?.toDomain(),p.inventoryStatus?.let{enumValueOf<SeatInventoryStatus>(it)},p.unavailableReason)})})
fun SeatLockDto.toDomain()=SeatLock(id,showtimeId,enumValueOf(status),serverTime,createdAt,expiresAt,items.map{LockedSeat(it.seatId,it.rowLabel,it.seatLabel,it.priceZoneId,it.unitPrice.toDomain())},totalPrice.toDomain(),releaseReason)

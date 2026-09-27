package me.ibrahim.moviesapp.compose.domain.seat
interface SeatRepository { suspend fun snapshot(showtimeId:String):SeatResult<SeatLayoutSnapshot>; suspend fun lock(showtimeId:String,seatIds:List<String>,version:Long,key:String):SeatResult<SeatLock>; suspend fun getLock(lockId:String):SeatResult<SeatLock>; suspend fun release(lockId:String):SeatResult<SeatLock> }

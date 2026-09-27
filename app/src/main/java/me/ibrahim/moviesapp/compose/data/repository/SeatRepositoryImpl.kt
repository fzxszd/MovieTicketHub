package me.ibrahim.moviesapp.compose.data.repository

import android.util.Log
import me.ibrahim.moviesapp.compose.data.dto.LockRequestDto
import me.ibrahim.moviesapp.compose.data.dto.SeatLockDto
import me.ibrahim.moviesapp.compose.data.mappers.toDomain
import me.ibrahim.moviesapp.compose.data.network.SeatRemoteApi
import me.ibrahim.moviesapp.compose.domain.seat.*

class SeatRepositoryImpl(private val api: SeatRemoteApi) : SeatRepository {
    private inline fun <T, R> SeatResult<T>.map(f: (T) -> R): SeatResult<R> = when (this) {
        is SeatResult.Success -> runCatching { SeatResult.Success(f(value)) }
            .getOrElse { error ->
                Log.e("SeatRepository", "Failed to map seat response", error)
                SeatResult.Failure("INVALID_DATA")
            }
        is SeatResult.Failure -> this
    }

    override suspend fun snapshot(id: String) = api.snapshot(id).map { it.toDomain() }

    override suspend fun lock(
        id: String,
        seats: List<String>,
        version: Long,
        key: String,
    ) = api.lock(id, LockRequestDto(seats, version), key).map { it.toDomainForPayment() }

    override suspend fun getLock(id: String) = api.getLock(id).map { it.toDomainForPayment() }

    override suspend fun release(id: String) = api.release(id).map { it.toDomainForPayment() }

    /** Normalize server lock states before the ViewModel decides whether to navigate. */
    private fun SeatLockDto.toDomainForPayment(): SeatLock {
        val normalizedStatus = when (status.trim().uppercase()) {
            "ACTIVE", "LOCKED", "LOCKED_BY_ME" -> SeatLockStatus.ACTIVE
            "RELEASED" -> SeatLockStatus.RELEASED
            "EXPIRED" -> SeatLockStatus.EXPIRED
            "CONVERTED" -> SeatLockStatus.CONVERTED
            "INVALIDATED" -> SeatLockStatus.INVALIDATED
            else -> throw IllegalArgumentException("Unknown seat lock status: $status")
        }
        return SeatLock(
            id = id,
            showtimeId = showtimeId,
            status = normalizedStatus,
            serverTime = serverTime,
            createdAt = createdAt,
            expiresAt = expiresAt,
            items = items.map {
                LockedSeat(
                    seatId = it.seatId,
                    rowLabel = it.rowLabel,
                    seatLabel = it.seatLabel,
                    priceZoneId = it.priceZoneId,
                    unitPrice = it.unitPrice.toDomain(),
                )
            },
            totalPrice = totalPrice.toDomain(),
            releaseReason = releaseReason,
        )
    }
}

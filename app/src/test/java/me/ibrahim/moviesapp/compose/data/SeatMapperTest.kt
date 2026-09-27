package me.ibrahim.moviesapp.compose.data

import me.ibrahim.moviesapp.compose.data.dto.MoneyDto
import me.ibrahim.moviesapp.compose.data.dto.SeatLayoutDto
import me.ibrahim.moviesapp.compose.data.dto.SeatPositionDto
import me.ibrahim.moviesapp.compose.data.dto.SeatRowDto
import me.ibrahim.moviesapp.compose.data.mappers.toDomain
import me.ibrahim.moviesapp.compose.domain.seat.SeatInventoryStatus
import me.ibrahim.moviesapp.compose.domain.seat.SeatPositionType
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SeatMapperTest {
    private fun layout(position: SeatPositionDto) = SeatLayoutDto("show", "aud", "Hall", "Screen", "2026-09-25T02:00:00Z", 7, listOf(SeatRowDto(0, "A", listOf(position))))

    @Test fun maps_authoritative_position_money_and_utc_time() {
        val result = layout(SeatPositionDto("A-01", 0, 0, "A", "1", "SEAT", "standard", MoneyDto(4500, "CNY"), "AVAILABLE")).toDomain()
        assertEquals("2026-09-25T02:00:00Z", result.serverTime)
        assertEquals(SeatPositionType.SEAT, result.rows.single().positions.single().positionType)
        assertEquals(SeatInventoryStatus.AVAILABLE, result.rows.single().positions.single().inventoryStatus)
        assertEquals(4500L, result.rows.single().positions.single().price?.amountMinor)
    }

    @Test fun rejects_unknown_server_enums_instead_of_guessing() {
        try { layout(SeatPositionDto("A-01", 0, 0, "A", "1", "BROKEN", "standard", null, "AVAILABLE")).toDomain(); fail("Unknown position type must be rejected") } catch (_: IllegalArgumentException) { }
        try { layout(SeatPositionDto("A-01", 0, 0, "A", "1", "SEAT", "standard", null, "BROKEN")).toDomain(); fail("Unknown inventory state must be rejected") } catch (_: IllegalArgumentException) { }
    }
}

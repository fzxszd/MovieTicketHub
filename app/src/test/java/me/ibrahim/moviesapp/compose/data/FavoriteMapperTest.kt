package me.ibrahim.moviesapp.compose.data

import me.ibrahim.moviesapp.compose.data.dto.FavoriteChangeDto
import me.ibrahim.moviesapp.compose.data.mappers.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteMapperTest {
    @Test fun `remote change maps to account scoped confirmed state`() {
        val entity = FavoriteChangeDto(42, true, 7, "2026-09-26T00:00:00Z", null).toEntity("account-a")
        assertEquals("account-a", entity.accountId)
        assertEquals(42, entity.movieId)
        assertTrue(entity.confirmedState)
        assertEquals(7, entity.serverRevision)
        assertEquals("CONFIRMED", entity.syncStatus)
    }
}

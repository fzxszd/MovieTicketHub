package me.ibrahim.moviesapp.compose.presentation.favorite

import me.ibrahim.moviesapp.compose.domain.favorite.FavoriteSyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteActionTest {
    @Test fun `pending and confirmed are distinct states`() {
        assertEquals("PENDING", FavoriteSyncStatus.PENDING.name)
        assertEquals("CONFIRMED", FavoriteSyncStatus.CONFIRMED.name)
    }
}

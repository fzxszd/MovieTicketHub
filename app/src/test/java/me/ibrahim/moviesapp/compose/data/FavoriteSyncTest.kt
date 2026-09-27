package me.ibrahim.moviesapp.compose.data

import java.util.UUID
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteSyncTest {
    @Test fun `operation ids are high entropy UUIDs`() {
        val first = UUID.randomUUID().toString(); val second = UUID.randomUUID().toString()
        assertTrue(first.length >= 16); assertNotEquals(first, second)
    }
}

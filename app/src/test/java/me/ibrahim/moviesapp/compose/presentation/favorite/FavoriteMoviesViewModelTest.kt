package me.ibrahim.moviesapp.compose.presentation.favorite

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteMoviesViewModelTest {
    @Test fun `empty state has no private content`() {
        assertEquals(0, FavoriteMoviesState().favoriteMovies.size)
        assertEquals(false, FavoriteMoviesState().isLoading)
    }
}

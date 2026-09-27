package me.ibrahim.moviesapp.compose.presentation.movies_list.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import me.ibrahim.moviesapp.compose.domain.Movie

@Composable
fun MoviesList(movies: List<Movie>, onMovieClick: (Movie) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(movies, key = { movie -> movie.id }) { movie ->
            MovieItem(movie = movie, onClick = { onMovieClick(movie) })
        }
    }
}
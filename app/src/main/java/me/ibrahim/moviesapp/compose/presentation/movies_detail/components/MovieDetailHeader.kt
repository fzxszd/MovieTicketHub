package me.ibrahim.moviesapp.compose.presentation.movies_detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.domain.Movie

@Composable
fun MovieDetailHeader(
    modifier: Modifier = Modifier,
    movie: Movie
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {

        AsyncImage(
            model = movie.posterPath,
            contentDescription = movie.title,
            modifier = Modifier
                .matchParentSize(),
            alpha = 0.8f,
            alignment = Alignment.TopCenter,
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colorResource(id = R.color.black2),
                            colorResource(id = R.color.black1),
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(0f, Float.POSITIVE_INFINITY)
                    )
                )
        )

        AsyncImage(
            model = movie.posterPath,
            contentDescription = movie.title,
            modifier = Modifier
                .width(200.dp)
                .padding(bottom = 10.dp)
                .aspectRatio(ratio = 2 / 3f)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp, color = Color.White,
                    shape = RoundedCornerShape(10.dp)
                )
                .align(Alignment.BottomCenter)
        )
    }
}
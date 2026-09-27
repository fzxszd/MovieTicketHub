package me.ibrahim.moviesapp.compose.presentation.movies_detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.domain.Movie
import me.ibrahim.moviesapp.compose.presentation.movies_detail.MovieDetailState
import kotlin.math.round

@Composable
fun MovieDetailContent(
    modifier: Modifier = Modifier,
    state: MovieDetailState
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            modifier = Modifier.padding(vertical = 5.dp),
            text = state.movie.title ?: "The Gardener",
            style = TextStyle(
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        )

        Row(
            horizontalArrangement = Arrangement.Absolute.spacedBy(10.dp),
        ) {
            TitledIcon(
                icon = Icons.Default.Star,
                title = "${round((state.movie.voteAverage ?: 0.0) * 10) / 10.0}"
            )

//            TitledIcon(
//                icon = Icons.Default.AccessTime,
//                title = "178 min"
//            )

            TitledIcon(
                icon = Icons.Default.DateRange,
                title = "${state.movie.releaseDate}"
            )
        }

        Spacer(modifier = Modifier.height(25.dp))

        Text(
            text = stringResource(id = R.string.summary),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        )

        Text(
            text = state.movie.overview ?: state.movie.title ?: "",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            textAlign = TextAlign.Start,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White
            )
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.actors.isNotEmpty()) {
            ActorsList(actors = state.actors)
        }
    }
}
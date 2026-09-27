package me.ibrahim.moviesapp.compose.presentation.movies_detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.domain.Actor

@Composable
fun ActorsList(
    modifier: Modifier = Modifier,
    actors: List<Actor>
) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(id = R.string.actors),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = actors,
                key = { index, actor ->
                    actor.creditId ?: "${actor.id}:${actor.name}:$index"
                }
            ) { _, actor ->
                ActorItem(actor = actor)
            }
        }
    }
}

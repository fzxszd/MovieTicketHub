package me.ibrahim.moviesapp.compose.presentation.movies_detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import me.ibrahim.moviesapp.compose.BuildConfig
import me.ibrahim.moviesapp.compose.domain.Actor

@Composable
fun ActorItem(
    modifier: Modifier = Modifier,
    actor: Actor
) {
    val profileImageUrl = actor.profilePath?.takeIf { it.isNotBlank() }?.let { path ->
        if (path.startsWith("http://") || path.startsWith("https://")) {
            path.replace("/w.h/", "/170.249/")
        } else {
            "${BuildConfig.POSTER_IMAGES_BASEURL}${path.ensureLeadingSlash()}"
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.width(100.dp)
    ) {
        val imageModifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .border(
                width = 0.5.dp,
                color = Color.White,
                shape = CircleShape
            )

        if (profileImageUrl == null) {
            Box(
                modifier = imageModifier.background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actor.name.orEmpty().take(1).uppercase(),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(profileImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = actor.name,
                contentScale = ContentScale.Crop,
                modifier = imageModifier
            )
        }
        Text(
            text = actor.name.orEmpty(),
            style = TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        actor.character?.takeIf { it.isNotBlank() }?.let { character ->
            Text(
                text = character,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun String.ensureLeadingSlash(): String {
    return if (startsWith('/')) this else "/$this"
}

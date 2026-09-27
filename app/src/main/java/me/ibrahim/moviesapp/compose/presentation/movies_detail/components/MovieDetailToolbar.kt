package me.ibrahim.moviesapp.compose.presentation.movies_detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.presentation.movies_detail.MovieDetailActions
import me.ibrahim.moviesapp.compose.presentation.movies_detail.MovieDetailState
import me.ibrahim.moviesapp.compose.domain.favorite.FavoriteSyncStatus

@Composable
fun MovieDetailToolbar(
    modifier: Modifier,
    state: MovieDetailState,
    onAction: (MovieDetailActions) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .statusBarsPadding(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        IconButton(onClick = {
            onAction(MovieDetailActions.GoBack)
        }) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = stringResource(id = R.string.back),
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
            )
        }

        IconButton(enabled = state.favoriteStatus != FavoriteSyncStatus.SYNCING, onClick = { onAction(MovieDetailActions.MarkFavorite(state.movie)) }) {
            Icon(
                imageVector = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (state.isFavorite) "取消收藏（${state.favoriteStatus.name}）" else "收藏（${state.favoriteStatus.name}）",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
            )
        }

    }
}

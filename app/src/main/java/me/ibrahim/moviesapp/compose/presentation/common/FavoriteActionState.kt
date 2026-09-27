package me.ibrahim.moviesapp.compose.presentation.common

import me.ibrahim.moviesapp.compose.domain.favorite.FavoriteSyncStatus

data class FavoriteActionState(val status: FavoriteSyncStatus = FavoriteSyncStatus.CONFIRMED, val message: String? = null)

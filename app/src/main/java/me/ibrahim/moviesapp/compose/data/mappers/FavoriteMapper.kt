package me.ibrahim.moviesapp.compose.data.mappers

import me.ibrahim.moviesapp.compose.data.database.FavoriteStateEntity
import me.ibrahim.moviesapp.compose.data.dto.FavoriteChangeDto
import me.ibrahim.moviesapp.compose.domain.favorite.Favorite
import me.ibrahim.moviesapp.compose.domain.favorite.FavoriteSyncStatus

fun FavoriteStateEntity.toDomain() = Favorite(movieId, desiredState, confirmedState, runCatching { FavoriteSyncStatus.valueOf(syncStatus) }.getOrDefault(FavoriteSyncStatus.PENDING), title, posterPath, availability, lastErrorCode)
fun FavoriteChangeDto.toEntity(accountId:String) = FavoriteStateEntity(accountId,movieId,isFavorite,isFavorite,serverRevision,"CONFIRMED",null,movie?.title,movie?.posterUrl,null,movie?.availability ?: "AVAILABLE")

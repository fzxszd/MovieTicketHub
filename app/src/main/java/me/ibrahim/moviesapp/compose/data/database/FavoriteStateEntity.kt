package me.ibrahim.moviesapp.compose.data.database

import androidx.room.Entity

@Entity(tableName = "FavoriteState", primaryKeys = ["accountId", "movieId"])
data class FavoriteStateEntity(
    val accountId: String, val movieId: Int, val confirmedState: Boolean,
    val desiredState: Boolean, val serverRevision: Long, val syncStatus: String,
    val lastErrorCode: String? = null, val title: String? = null,
    val posterPath: String? = null, val releaseDate: String? = null,
    val availability: String = "AVAILABLE"
)

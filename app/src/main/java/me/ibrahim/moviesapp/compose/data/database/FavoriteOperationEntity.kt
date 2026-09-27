package me.ibrahim.moviesapp.compose.data.database

import androidx.room.Entity

@Entity(tableName = "FavoriteOperation", primaryKeys = ["clientOperationId"])
data class FavoriteOperationEntity(
    val clientOperationId: String, val accountId: String, val movieId: Int,
    val targetState: Boolean, val localSequence: Long, val status: String = "PENDING",
    val retryCount: Int = 0, val lastErrorCode: String? = null
)

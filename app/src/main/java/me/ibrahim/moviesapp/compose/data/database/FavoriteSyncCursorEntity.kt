package me.ibrahim.moviesapp.compose.data.database

import androidx.room.Entity

@Entity(tableName = "FavoriteSyncCursor", primaryKeys = ["accountId"])
data class FavoriteSyncCursorEntity(val accountId: String, val lastServerRevision: Long = 0, val lastSyncAt: String? = null)

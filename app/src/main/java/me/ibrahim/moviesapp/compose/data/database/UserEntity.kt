package me.ibrahim.moviesapp.compose.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "UserAccount", indices = [Index(value = ["userId"], unique = true)])
data class UserEntity(
    @PrimaryKey val email: String,
    val userId: String? = null,
    val name: String,
    val avatarUri: String? = null
)

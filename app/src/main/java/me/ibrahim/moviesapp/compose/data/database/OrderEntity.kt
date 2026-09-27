package me.ibrahim.moviesapp.compose.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "MovieOrder")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true)
    val orderId: Int = 0,
    val userEmail: String, // Associate with user
    val movieId: Int,
    val movieTitle: String,
    val moviePoster: String?,
    val cinemaName: String = "",
    val showTime: String = "",
    val hallName: String = "",
    val seatInfo: String = "",
    val price: String,
    val timestamp: Long = System.currentTimeMillis()
)

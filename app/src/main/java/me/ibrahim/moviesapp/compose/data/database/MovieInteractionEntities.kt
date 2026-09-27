package me.ibrahim.moviesapp.compose.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "MovieComment")
data class CommentEntity(
    @PrimaryKey val id: Int, // 同步系统通常由服务端或全局计数器生成 ID
    val movieId: Int,
    val userEmail: String,
    val userName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val parentId: Int? = null,
    val likes: Int = 0
)

@Entity(tableName = "MovieRating", primaryKeys = ["movieId", "userEmail"])
data class RatingEntity(
    val movieId: Int,
    val userEmail: String,
    val rating: Int
)

@Entity(tableName = "CommentLike", primaryKeys = ["commentId", "userEmail"])
data class CommentLikeEntity(
    val commentId: Int,
    val userEmail: String
)

package me.ibrahim.moviesapp.compose.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserSyncData(
    val name: String = "",
    val avatarUri: String? = null,
    val orders: List<OrderEntitySyncDto> = emptyList(),
    val ratings: List<RatingEntitySyncDto> = emptyList(),
    val comments: List<CommentEntitySyncDto> = emptyList(),
    @SerialName("globalComments") val globalComments: List<CommentEntitySyncDto> = emptyList(),
    // 增加点赞记录同步
    val likedCommentIds: List<Int> = emptyList()
)

@Serializable
data class MovieEntitySyncDto(
    val id: Int,
    val backdropPath: String? = null,
    val originalLanguage: String? = null,
    val originalTitle: String? = null,
    val overview: String? = null,
    val popularity: Double? = null,
    val posterPath: String? = null,
    val releaseDate: String? = null,
    val title: String? = null,
    val video: Boolean? = false,
    val voteAverage: Double? = 0.0,
    val voteCount: Int? = 0
)

@Serializable
data class OrderEntitySyncDto(
    val movieId: Int,
    val movieTitle: String = "",
    val moviePoster: String? = null,
    val cinemaName: String = "",
    val showTime: String = "",
    val hallName: String = "",
    val seatInfo: String = "",
    val price: String = "",
    val timestamp: Long = 0L
)

@Serializable
data class RatingEntitySyncDto(
    val movieId: Int,
    val rating: Int
)

@Serializable
data class CommentEntitySyncDto(
    val id: Int = 0,
    val movieId: Int = 0,
    val userEmail: String = "",
    val userName: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val parentId: Int? = null,
    val likes: Int = 0
)

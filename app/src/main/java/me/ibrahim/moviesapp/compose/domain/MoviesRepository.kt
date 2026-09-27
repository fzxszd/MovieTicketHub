package me.ibrahim.moviesapp.compose.domain

import kotlinx.coroutines.flow.Flow
import me.ibrahim.moviesapp.compose.data.database.CommentEntity
import me.ibrahim.moviesapp.compose.data.database.OrderEntity
import me.ibrahim.moviesapp.compose.data.database.UserEntity

interface MoviesRepository {

    suspend fun fetchNowPlayingMovies(): Result<List<Movie>, DataError.Remote>
    suspend fun fetchUpcomingMovies(): Result<List<Movie>, DataError.Remote>
    suspend fun fetchMovieActors(movieId: Int): Result<List<Actor>, DataError.Remote>
    suspend fun fetchMovieDetail(movieId: Int): Result<String, DataError.Remote>
    // 用户操作
    suspend fun updateUserProfile(user: UserEntity)
    suspend fun clearAllData()

    // 自动同步
    fun startAutoSync(email: String)
    fun stopAutoSync()

    // 数据库操作
    // 订单操作
    fun getAllOrders(userEmail: String): Flow<List<OrderEntity>>
    suspend fun deleteOrder(order: OrderEntity)
    fun hasPurchasedMovie(movieId: Int, userEmail: String): Flow<Boolean>

    // 评分与评论
    suspend fun saveRating(movieId: Int, userEmail: String, rating: Int)
    fun getRating(movieId: Int, userEmail: String): Flow<Int?>
    suspend fun saveComment(movieId: Int, userEmail: String, userName: String, content: String, parentId: Int? = null)
    fun getComments(movieId: Int): Flow<List<CommentEntity>>
    
    // 点赞功能
    suspend fun toggleCommentLike(commentId: Int, userEmail: String)
    fun isCommentLiked(commentId: Int, userEmail: String): Flow<Boolean>
    fun getLikedCommentIds(userEmail: String): Flow<Set<Int>>

    // 服务器同步
    suspend fun pushDataToPC(email: String)
    suspend fun pullDataFromPC(email: String)

    // --- 推荐系统共享状态 ---
}

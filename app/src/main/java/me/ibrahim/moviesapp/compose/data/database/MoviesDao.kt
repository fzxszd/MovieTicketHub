package me.ibrahim.moviesapp.compose.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface MoviesDao {

    // User operations
    @Upsert
    suspend fun upsertUser(user: UserEntity)

    @Query("SELECT * FROM UserAccount WHERE email = :email")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM UserAccount WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("DELETE FROM UserAccount")
    suspend fun deleteAllUsers()

    @Query("DELETE FROM UserAccount WHERE email = :email")
    suspend fun deleteUserByEmail(email: String)

    // Favorite Movie operations
    @Upsert
    suspend fun upsertFavorite(movieEntity: MovieEntity)

    @Query("SELECT * FROM FavoriteMovie WHERE userEmail = :userEmail")
    fun getFavoriteMovies(userEmail: String): Flow<List<MovieEntity>>

    @Query("SELECT id FROM FavoriteMovie WHERE userEmail = :userEmail")
    fun getFavoriteMoviesIds(userEmail: String): Flow<List<Int>>

    @Query("DELETE FROM FavoriteMovie WHERE id = :movieId AND userEmail = :userEmail")
    suspend fun deleteFavoriteMovie(movieId: Int, userEmail: String)

    @Query("DELETE FROM FavoriteMovie WHERE userEmail = :userEmail")
    suspend fun deleteFavoritesByUser(userEmail: String)

    @Query("DELETE FROM FavoriteMovie")
    suspend fun deleteAllFavorites()

    // Order operations
    @Upsert
    suspend fun upsertOrder(orderEntity: OrderEntity)

    @Query("SELECT * FROM MovieOrder WHERE userEmail = :userEmail ORDER BY timestamp DESC")
    fun getAllOrders(userEmail: String): Flow<List<OrderEntity>>

    @Query("SELECT COUNT(*) > 0 FROM MovieOrder WHERE movieId = :movieId AND userEmail = :userEmail")
    fun hasPurchasedMovie(movieId: Int, userEmail: String): Flow<Boolean>

    @Delete
    suspend fun deleteOrder(orderEntity: OrderEntity)

    @Query("DELETE FROM MovieOrder WHERE userEmail = :userEmail")
    suspend fun deleteOrdersByUser(userEmail: String)

    @Query("DELETE FROM MovieOrder")
    suspend fun deleteAllOrders()

    // Rating and Comment operations
    @Upsert
    suspend fun upsertRating(ratingEntity: RatingEntity)

    @Query("SELECT rating FROM MovieRating WHERE movieId = :movieId AND userEmail = :userEmail")
    fun getMovieRating(movieId: Int, userEmail: String): Flow<Int?>

    @Query("SELECT * FROM MovieRating WHERE userEmail = :userEmail")
    suspend fun getAllRatings(userEmail: String): List<RatingEntity>

    @Query("DELETE FROM MovieRating WHERE userEmail = :userEmail")
    suspend fun deleteRatingsByUser(userEmail: String)

    @Query("DELETE FROM MovieRating")
    suspend fun deleteAllRatings()

    @Upsert
    suspend fun upsertComment(commentEntity: CommentEntity)

    @Query("SELECT * FROM MovieComment WHERE movieId = :movieId ORDER BY timestamp DESC")
    fun getCommentsForMovie(movieId: Int): Flow<List<CommentEntity>>

    @Query("SELECT * FROM MovieComment WHERE userEmail = :userEmail")
    suspend fun getAllComments(userEmail: String): List<CommentEntity>

    @Query("SELECT * FROM MovieComment")
    suspend fun getGlobalAllComments(): List<CommentEntity>

    @Query("DELETE FROM MovieComment")
    suspend fun deleteAllComments()

    @Query("DELETE FROM MovieComment WHERE userEmail = :userEmail")
    suspend fun deleteCommentsByUser(userEmail: String)

    // Like operations
    @Upsert
    suspend fun upsertLike(like: CommentLikeEntity)

    @Delete
    suspend fun deleteLike(like: CommentLikeEntity)

    @Query("SELECT COUNT(*) FROM CommentLike WHERE commentId = :commentId")
    fun getCommentLikeCount(commentId: Int): Flow<Int>

    @Query("SELECT COUNT(*) > 0 FROM CommentLike WHERE commentId = :commentId AND userEmail = :userEmail")
    fun isCommentLikedByUser(commentId: Int, userEmail: String): Flow<Boolean>

    @Query("SELECT commentId FROM CommentLike WHERE userEmail = :userEmail")
    fun getLikedCommentIds(userEmail: String): Flow<List<Int>>

    @Query("DELETE FROM CommentLike WHERE userEmail = :userEmail")
    suspend fun deleteLikesByUser(userEmail: String)

    @Query("DELETE FROM CommentLike")
    suspend fun deleteAllLikes()

    // 修改点赞数
    @Query("UPDATE MovieComment SET likes = likes + 1 WHERE id = :commentId")
    suspend fun incrementLikes(commentId: Int)

    @Query("UPDATE MovieComment SET likes = CASE WHEN likes > 0 THEN likes - 1 ELSE 0 END WHERE id = :commentId")
    suspend fun decrementLikes(commentId: Int)
}

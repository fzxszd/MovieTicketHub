package me.ibrahim.moviesapp.compose.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MovieEntity::class,
        OrderEntity::class,
        CommentEntity::class,
        RatingEntity::class,
        UserEntity::class,
        CommentLikeEntity::class,
        FavoriteStateEntity::class,
        FavoriteOperationEntity::class,
        FavoriteSyncCursorEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class MoviesDatabase : RoomDatabase() {
    abstract val moviesDao: MoviesDao
    abstract val favoriteDao: FavoriteDao
}

const val FavoriteMoviesDb = "favoriteMovies.db"

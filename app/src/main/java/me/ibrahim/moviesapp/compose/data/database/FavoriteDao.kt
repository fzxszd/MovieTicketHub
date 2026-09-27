package me.ibrahim.moviesapp.compose.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM FavoriteState WHERE accountId=:accountId AND desiredState=1 ORDER BY movieId")
    fun observeFavorites(accountId: String): Flow<List<FavoriteStateEntity>>
    @Query("SELECT * FROM FavoriteState WHERE accountId=:accountId AND movieId=:movieId LIMIT 1")
    suspend fun state(accountId: String, movieId: Int): FavoriteStateEntity?
    @Query("SELECT COALESCE(MAX(localSequence),0) FROM FavoriteOperation WHERE accountId=:accountId")
    suspend fun maxSequence(accountId: String): Long
    @Query("SELECT * FROM FavoriteOperation WHERE accountId=:accountId AND status IN ('PENDING','SYNCING') ORDER BY localSequence LIMIT :limit")
    suspend fun pending(accountId: String, limit: Int = 100): List<FavoriteOperationEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertState(entity: FavoriteStateEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertOperation(entity: FavoriteOperationEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCursor(entity: FavoriteSyncCursorEntity)
    @Query("UPDATE FavoriteOperation SET status=:status,lastErrorCode=:errorCode WHERE clientOperationId=:id") suspend fun updateOperation(id:String,status:String,errorCode:String?=null)
    @Query("SELECT * FROM FavoriteSyncCursor WHERE accountId=:accountId LIMIT 1") suspend fun cursor(accountId: String): FavoriteSyncCursorEntity?
    @Query("DELETE FROM FavoriteState WHERE accountId=:accountId") suspend fun clearStates(accountId: String)
    @Query("DELETE FROM FavoriteOperation WHERE accountId=:accountId") suspend fun clearOperations(accountId: String)
    @Query("DELETE FROM FavoriteSyncCursor WHERE accountId=:accountId") suspend fun clearCursor(accountId: String)
    @Transaction
    suspend fun clearAccount(accountId: String) { clearStates(accountId); clearOperations(accountId); clearCursor(accountId) }
}

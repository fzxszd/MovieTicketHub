package me.ibrahim.moviesapp.compose.domain.favorite

import kotlinx.coroutines.flow.Flow

enum class FavoriteSyncStatus { CONFIRMED, PENDING, SYNCING, FAILED }
data class Favorite(val movieId: Int, val isFavorite: Boolean, val confirmed: Boolean, val status: FavoriteSyncStatus, val title: String? = null, val posterPath: String? = null, val availability: String = "AVAILABLE", val errorCode: String? = null)
sealed interface FavoriteResult<out T> { data class Success<T>(val value:T): FavoriteResult<T>; data class Failure(val code:String, val retryable:Boolean=true): FavoriteResult<Nothing> }

interface FavoriteRepository {
    fun observeFavorites(accountId: String): Flow<List<Favorite>>
    fun observe(accountId: String, movieId: Int): Flow<Favorite?>
    suspend fun setFavorite(accountId: String, movieId: Int, targetState: Boolean, snapshot: Favorite? = null): FavoriteResult<Favorite>
    suspend fun sync(accountId: String): FavoriteResult<Unit>
    suspend fun retry(accountId: String): FavoriteResult<Unit> = sync(accountId)
    suspend fun onAccountStarted(accountId: String)
    suspend fun onAccountStopped(accountId: String, clear: Boolean = true)
    val confirmedRevision: Flow<Long>
}

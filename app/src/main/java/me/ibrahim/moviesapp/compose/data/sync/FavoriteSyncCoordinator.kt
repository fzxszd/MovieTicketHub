package me.ibrahim.moviesapp.compose.data.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import me.ibrahim.moviesapp.compose.domain.favorite.FavoriteResult

/** Single in-process coordinator entry point; repository owns account/session checks. */
class FavoriteSyncCoordinator(private val syncBlock: suspend (String)->FavoriteResult<Unit>) {
    private val mutex = Mutex()
    suspend fun sync(accountId:String): FavoriteResult<Unit> = mutex.withLock {
        var last: FavoriteResult<Unit> = FavoriteResult.Failure("NETWORK")
        repeat(3) { attempt ->
            last = syncBlock(accountId)
            if (last is FavoriteResult.Success || last is FavoriteResult.Failure && !(last as FavoriteResult.Failure).retryable) return@withLock last
            if (attempt < 2) delay(100L shl attempt)
        }
        last
    }
}

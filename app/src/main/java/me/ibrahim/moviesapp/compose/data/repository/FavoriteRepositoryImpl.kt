package me.ibrahim.moviesapp.compose.data.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID
import me.ibrahim.moviesapp.compose.data.database.*
import me.ibrahim.moviesapp.compose.data.dto.*
import me.ibrahim.moviesapp.compose.data.mappers.toDomain
import me.ibrahim.moviesapp.compose.data.mappers.toEntity
import me.ibrahim.moviesapp.compose.data.network.*
import me.ibrahim.moviesapp.compose.data.sync.FavoriteSyncCoordinator
import me.ibrahim.moviesapp.compose.domain.favorite.*
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository

class FavoriteRepositoryImpl(private val dao:FavoriteDao, private val api:FavoriteRemoteApi, private val auth:AuthRepository, context:Context):FavoriteRepository {
    private val prefs=context.getSharedPreferences("favorite_sync",Context.MODE_PRIVATE)
    private val deviceId=prefs.getString("device_id",null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id",it).apply() }
    private val _confirmedRevision=MutableStateFlow(0L)
    override val confirmedRevision:Flow<Long> = _confirmedRevision.asStateFlow()
    private val coordinator=FavoriteSyncCoordinator { account -> syncInternal(account) }
    override fun observeFavorites(accountId:String):Flow<List<Favorite>> = dao.observeFavorites(accountId).map { list->list.map { it.toDomain() } }
    override fun observe(accountId:String,movieId:Int):Flow<Favorite?> = dao.observeFavorites(accountId).map { it.firstOrNull { row->row.movieId==movieId }?.toDomain() }
    override suspend fun onAccountStarted(accountId:String) { if(auth.currentUser()?.id==accountId) sync(accountId) }
    override suspend fun onAccountStopped(accountId:String,clear:Boolean) { if(clear) dao.clearAccount(accountId) }
    override suspend fun setFavorite(accountId:String,movieId:Int,targetState:Boolean,snapshot:Favorite?):FavoriteResult<Favorite> {
        if(auth.currentUser()?.id!=accountId) return FavoriteResult.Failure("UNAUTHENTICATED",false)
        val seq=dao.maxSequence(accountId)+1; val op=UUID.randomUUID().toString()
        val old=dao.state(accountId,movieId)
        dao.upsertState(FavoriteStateEntity(accountId,movieId,old?.confirmedState ?: false,targetState,old?.serverRevision ?: 0,"PENDING",null,snapshot?.title,snapshot?.posterPath,null,snapshot?.availability ?: "AVAILABLE"))
        dao.upsertOperation(FavoriteOperationEntity(op,accountId,movieId,targetState,seq))
        val syncResult=sync(accountId)
        val latest=dao.state(accountId,movieId)?.toDomain() ?: return FavoriteResult.Failure("LOCAL_STATE")
        return when(syncResult){is FavoriteResult.Success->FavoriteResult.Success(latest);is FavoriteResult.Failure->FavoriteResult.Failure(syncResult.code,syncResult.retryable)}
    }
    override suspend fun sync(accountId:String):FavoriteResult<Unit> = coordinator.sync(accountId)
    private suspend fun syncInternal(accountId:String):FavoriteResult<Unit> {
        val token=auth.currentToken() ?: return FavoriteResult.Failure("UNAUTHENTICATED",false)
        if(auth.currentUser()?.id!=accountId) return FavoriteResult.Failure("ACCOUNT_CHANGED",false)
        val cursor=dao.cursor(accountId)?.lastServerRevision ?: 0
        val operations=dao.pending(accountId,100)
        val requestOps=operations.map { FavoriteOperationDto(it.clientOperationId,it.movieId,it.targetState,it.localSequence) }
        return when(val result=api.sync(token,FavoriteSyncRequestDto(deviceId,cursor,requestOps))){
            is FavoriteApiResult.Failure -> FavoriteResult.Failure(result.code,result.code=="NETWORK")
            is FavoriteApiResult.Success -> {
                result.value.operationResults.forEach { op ->
                    val current=dao.state(accountId,op.movieId)
                    dao.updateOperation(op.clientOperationId,if(op.result=="REJECTED") "REJECTED" else "CONFIRMED",op.errorCode)
                    dao.upsertState(FavoriteStateEntity(accountId,op.movieId,op.authoritativeState,current?.desiredState ?: op.authoritativeState,op.serverRevision,if(op.result=="REJECTED") "FAILED" else "CONFIRMED",op.errorCode,current?.title,current?.posterPath,current?.releaseDate,current?.availability ?: "AVAILABLE"))
                }
                result.value.changes.forEach { change ->
                    val current=dao.state(accountId,change.movieId); dao.upsertState(change.toEntity(accountId).copy(desiredState=current?.desiredState ?: change.isFavorite))
                }
                dao.upsertCursor(FavoriteSyncCursorEntity(accountId,result.value.serverRevision))
                _confirmedRevision.value=result.value.serverRevision
                FavoriteResult.Success(Unit)
            }
        }
    }
}

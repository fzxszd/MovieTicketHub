package me.ibrahim.moviesapp.compose.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import me.ibrahim.moviesapp.compose.data.dto.*
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository

sealed interface FavoriteApiResult<out T> { data class Success<T>(val value:T):FavoriteApiResult<T>; data class Failure(val code:String,val status:Int=0):FavoriteApiResult<Nothing> }
interface FavoriteRemoteApi { suspend fun list(token:String,after:Long,limit:Int):FavoriteApiResult<FavoriteChangesDto>; suspend fun set(token:String,movieId:Int,key:String,body:FavoriteSetRequestDto):FavoriteApiResult<FavoriteOperationResultDto>; suspend fun sync(token:String,body:FavoriteSyncRequestDto):FavoriteApiResult<FavoriteChangesDto> }
@kotlinx.serialization.Serializable data class FavoriteSyncRequestDto(val deviceId:String,val sinceRevision:Long,val operations:List<FavoriteOperationDto>)

class FavoriteRemoteApiImpl(private val client:HttpClient):FavoriteRemoteApi {
    private val base get()=RemoteApiEndpoints.AUTH_BASE_URL
    override suspend fun list(token:String,after:Long,limit:Int)=request<FavoriteChangesDto>{client.get("$base/v1/favorites"){bearerAuth(token);parameter("afterRevision",after);parameter("limit",limit)}}
    override suspend fun set(token:String,movieId:Int,key:String,body:FavoriteSetRequestDto)=request<FavoriteOperationResultDto>{client.put("$base/v1/favorites/$movieId"){bearerAuth(token);headers.append("Idempotency-Key",key);contentType(ContentType.Application.Json);setBody(body)}}
    override suspend fun sync(token:String,body:FavoriteSyncRequestDto)=request<FavoriteChangesDto>{client.post("$base/v1/favorites/sync"){bearerAuth(token);contentType(ContentType.Application.Json);setBody(body)}}
    private suspend inline fun <reified T> request(block:suspend()->HttpResponse):FavoriteApiResult<T> = try { val response=block(); if(response.status.isSuccess()) FavoriteApiResult.Success(response.body()) else FavoriteApiResult.Failure("HTTP_${response.status.value}",response.status.value) } catch(_:Exception){FavoriteApiResult.Failure("NETWORK")}
}

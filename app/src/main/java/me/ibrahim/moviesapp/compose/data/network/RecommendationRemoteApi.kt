package me.ibrahim.moviesapp.compose.data.network
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import me.ibrahim.moviesapp.compose.data.dto.*
sealed interface RecommendationApiResult<out T>{data class Success<T>(val value:T):RecommendationApiResult<T>;data class Failure(val code:String,val status:Int=0):RecommendationApiResult<Nothing>}
interface RecommendationRemoteApi{suspend fun get(token:String?,limit:Int):RecommendationApiResult<RecommendationResponseDto>;suspend fun event(token:String?,recommendationId:String,body:RecommendationEventDto):RecommendationApiResult<Unit>}
class RecommendationRemoteApiImpl(private val client:HttpClient):RecommendationRemoteApi{
 private val base get()=RemoteApiEndpoints.AUTH_BASE_URL
 override suspend fun get(token:String?,limit:Int)=request<RecommendationResponseDto>{client.get("$base/v1/recommendations"){token?.let{bearerAuth(it)};parameter("limit",limit)}}
 override suspend fun event(token:String?,recommendationId:String,body:RecommendationEventDto)=request<Unit>{client.post("$base/v1/recommendations/$recommendationId/events"){token?.let{bearerAuth(it)};contentType(ContentType.Application.Json);setBody(body)}}
 private suspend inline fun <reified T> request(noinline block:suspend()->HttpResponse):RecommendationApiResult<T> {
   return try { val r=block(); if(r.status.isSuccess()) RecommendationApiResult.Success(if(T::class==Unit::class) Unit as T else r.body()) else RecommendationApiResult.Failure("HTTP_${r.status.value}",r.status.value) } catch(_:Exception) { RecommendationApiResult.Failure("NETWORK") }
 }
}

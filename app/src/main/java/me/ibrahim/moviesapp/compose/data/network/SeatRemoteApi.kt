package me.ibrahim.moviesapp.compose.data.network
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import me.ibrahim.moviesapp.compose.data.dto.*
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository
import me.ibrahim.moviesapp.compose.domain.seat.SeatResult
interface SeatRemoteApi { suspend fun snapshot(id:String):SeatResult<SeatLayoutDto>; suspend fun lock(id:String,body:LockRequestDto,key:String):SeatResult<SeatLockDto>; suspend fun getLock(id:String):SeatResult<SeatLockDto>; suspend fun release(id:String):SeatResult<SeatLockDto> }
class SeatRemoteApiImpl(private val client:HttpClient,private val auth:AuthRepository):SeatRemoteApi { private val base get()=RemoteApiEndpoints.AUTH_BASE_URL
 private suspend inline fun <reified T> call(block: suspend () -> HttpResponse): SeatResult<T> = try { val response = block(); if (response.status.isSuccess()) SeatResult.Success(response.body()) else SeatResult.Failure("HTTP_${response.status.value}") } catch (_: Exception) { SeatResult.Failure("NETWORK") }
 private fun HttpRequestBuilder.auth(){auth.currentToken()?.let{header(HttpHeaders.Authorization,"Bearer $it")}}
 override suspend fun snapshot(id:String)=call<SeatLayoutDto>{client.get("$base/v1/showtimes/$id/seats"){auth()}}
 override suspend fun lock(id:String,body:LockRequestDto,key:String)=call<SeatLockDto>{client.post("$base/v1/showtimes/$id/seat-locks"){auth();header("Idempotency-Key",key);contentType(ContentType.Application.Json);setBody(body)}}
 override suspend fun getLock(id:String)=call<SeatLockDto>{client.get("$base/v1/seat-locks/$id"){auth()}}
 override suspend fun release(id:String)=call<SeatLockDto>{client.delete("$base/v1/seat-locks/$id"){auth()}}
}

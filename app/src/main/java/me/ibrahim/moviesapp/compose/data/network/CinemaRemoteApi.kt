package me.ibrahim.moviesapp.compose.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import me.ibrahim.moviesapp.compose.data.dto.*
import me.ibrahim.moviesapp.compose.domain.cinema.*

interface CinemaRemoteApi {
    suspend fun dates(movieId:Int,cityCode:String):CinemaResult<ShowtimeDatesDto>
    suspend fun showtimes(query:ShowtimeQuery):CinemaResult<CinemaShowtimeResponseDto>
    suspend fun validate(showtimeId:String,version:Long):CinemaResult<ShowtimeValidationDto>
}

class CinemaRemoteApiImpl(private val client:HttpClient):CinemaRemoteApi {
    private val base get()=RemoteApiEndpoints.AUTH_BASE_URL
    override suspend fun dates(movieId:Int,cityCode:String)=request<ShowtimeDatesDto>{
        client.get("$base/v1/movies/$movieId/showtime-dates"){parameter("cityCode",cityCode)} }
    override suspend fun showtimes(query:ShowtimeQuery)=request<CinemaShowtimeResponseDto>{
        client.get("$base/v1/movies/${query.movieId}/cinema-showtimes") {
            parameter("cityCode",query.cityCode);parameter("date",query.localDate)
            query.districts.forEach { parameter("district",it) }
            parameter("startTime",query.startLocalTime);parameter("endTime",query.endLocalTime)
            parameter("minPriceMinor",query.minPriceMinor);parameter("maxPriceMinor",query.maxPriceMinor)
            parameter("sort",query.sort.wireName);parameter("latitude",query.latitude);parameter("longitude",query.longitude)
        }}
    override suspend fun validate(showtimeId:String,version:Long)=request<ShowtimeValidationDto>{
        client.get("$base/v1/showtimes/$showtimeId/validation"){parameter("observedVersion",version)} }
    private suspend inline fun <reified T> request(block:suspend()->HttpResponse):CinemaResult<T> = try {
        val response=block()
        if(response.status.isSuccess()) CinemaResult.Success(response.body()) else {
            val code=runCatching{response.body<CinemaApiErrorDto>().code}.getOrDefault("HTTP_${response.status.value}")
            CinemaResult.Failure(CinemaError.Service(code))
        }
    } catch(_:HttpRequestTimeoutException){CinemaResult.Failure(CinemaError.Timeout)}
      catch(_:Exception){CinemaResult.Failure(CinemaError.Network)}
}

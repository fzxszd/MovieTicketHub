package me.ibrahim.moviesapp.compose.data.repository

import me.ibrahim.moviesapp.compose.data.mappers.toDomain
import me.ibrahim.moviesapp.compose.data.network.CinemaRemoteApi
import me.ibrahim.moviesapp.compose.domain.cinema.*

class CinemaRepositoryImpl(private val remote:CinemaRemoteApi):CinemaRepository {
    override suspend fun availableDates(movieId:Int,cityCode:String):CinemaResult<List<String>> =
        remote.dates(movieId,cityCode).map { it.dates }
    override suspend fun showtimes(query:ShowtimeQuery):CinemaResult<CinemaShowtimeResult> {
        if(query.validationError()!=null)return CinemaResult.Failure(CinemaError.InvalidData)
        return remote.showtimes(query).mapCatching { it.toDomain(query) }
    }
    override suspend fun validate(showtimeId:String,observedVersion:Long):CinemaResult<ShowtimeValidation> =
        if(showtimeId.isBlank()||observedVersion<1) CinemaResult.Failure(CinemaError.InvalidData)
        else remote.validate(showtimeId,observedVersion).mapCatching { it.toDomain() }
}

private inline fun <T,R>CinemaResult<T>.map(transform:(T)->R):CinemaResult<R> = when(this){
    is CinemaResult.Success->CinemaResult.Success(transform(value));is CinemaResult.Failure->this}
private inline fun <T,R>CinemaResult<T>.mapCatching(transform:(T)->R):CinemaResult<R> = when(this){
    is CinemaResult.Success->runCatching{CinemaResult.Success(transform(value))}.getOrElse{CinemaResult.Failure(CinemaError.InvalidData)}
    is CinemaResult.Failure->this}

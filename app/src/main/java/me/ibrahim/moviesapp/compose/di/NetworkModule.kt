package me.ibrahim.moviesapp.compose.di

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import me.ibrahim.moviesapp.compose.data.network.MoviesRemoteApi
import me.ibrahim.moviesapp.compose.data.network.MoviesRemoteApiImpl
import me.ibrahim.moviesapp.compose.data.network.AuthRemoteApi
import me.ibrahim.moviesapp.compose.data.network.AuthRemoteApiImpl
import me.ibrahim.moviesapp.compose.data.network.CinemaRemoteApi
import me.ibrahim.moviesapp.compose.data.network.CinemaRemoteApiImpl
import me.ibrahim.moviesapp.compose.data.network.FavoriteRemoteApi
import me.ibrahim.moviesapp.compose.data.network.FavoriteRemoteApiImpl
import me.ibrahim.moviesapp.compose.data.network.SeatRemoteApi
import me.ibrahim.moviesapp.compose.data.network.SeatRemoteApiImpl
import me.ibrahim.moviesapp.compose.data.network.RecommendationRemoteApi
import me.ibrahim.moviesapp.compose.data.network.RecommendationRemoteApiImpl
import me.ibrahim.moviesapp.compose.data.network.OrderRemoteApi
import me.ibrahim.moviesapp.compose.data.network.OrderRemoteApiImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

private const val NETWORK_TIME_OUT = 15_000L

val networkModule = module {

    singleOf(::MoviesRemoteApiImpl).bind<MoviesRemoteApi>()
    singleOf(::AuthRemoteApiImpl).bind<AuthRemoteApi>()
    singleOf(::CinemaRemoteApiImpl).bind<CinemaRemoteApi>()
    singleOf(::FavoriteRemoteApiImpl).bind<FavoriteRemoteApi>()
    singleOf(::SeatRemoteApiImpl).bind<SeatRemoteApi>()
    singleOf(::RecommendationRemoteApiImpl).bind<RecommendationRemoteApi>()
    singleOf(::OrderRemoteApiImpl).bind<OrderRemoteApi>()

    single {
        HttpClient(Android) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.v("Ktor-Log", message)
                    }
                }
                // Authentication bodies and Authorization headers must never be logged.
                level = LogLevel.NONE
            }

            install(HttpTimeout) {
                requestTimeoutMillis = NETWORK_TIME_OUT
                connectTimeoutMillis = NETWORK_TIME_OUT
                socketTimeoutMillis = NETWORK_TIME_OUT
            }

            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                })
            }
        }
    }
}

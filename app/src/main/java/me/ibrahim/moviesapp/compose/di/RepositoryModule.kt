package me.ibrahim.moviesapp.compose.di

import me.ibrahim.moviesapp.compose.data.repository.MoviesRepositoryImpl
import me.ibrahim.moviesapp.compose.domain.MoviesRepository
import me.ibrahim.moviesapp.compose.data.repository.AuthRepositoryImpl
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository
import me.ibrahim.moviesapp.compose.data.repository.RoomUserAccountCache
import me.ibrahim.moviesapp.compose.data.repository.UserAccountCache
import me.ibrahim.moviesapp.compose.data.repository.CinemaRepositoryImpl
import me.ibrahim.moviesapp.compose.domain.cinema.CinemaRepository
import me.ibrahim.moviesapp.compose.data.repository.SeatRepositoryImpl
import me.ibrahim.moviesapp.compose.domain.seat.SeatRepository
import me.ibrahim.moviesapp.compose.data.repository.FavoriteRepositoryImpl
import me.ibrahim.moviesapp.compose.domain.favorite.FavoriteRepository
import me.ibrahim.moviesapp.compose.data.repository.RecommendationRepositoryImpl
import me.ibrahim.moviesapp.compose.domain.recommendation.RecommendationRepository
import me.ibrahim.moviesapp.compose.data.repository.OrderRepositoryImpl
import me.ibrahim.moviesapp.compose.domain.order.OrderRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val repositoryModule = module {
    singleOf(::MoviesRepositoryImpl).bind<MoviesRepository>()
    singleOf(::AuthRepositoryImpl).bind<AuthRepository>()
    singleOf(::RoomUserAccountCache).bind<UserAccountCache>()
    singleOf(::CinemaRepositoryImpl).bind<CinemaRepository>()
    singleOf(::SeatRepositoryImpl).bind<SeatRepository>()
    singleOf(::FavoriteRepositoryImpl).bind<FavoriteRepository>()
    singleOf(::RecommendationRepositoryImpl).bind<RecommendationRepository>()
    singleOf(::OrderRepositoryImpl).bind<OrderRepository>()
}

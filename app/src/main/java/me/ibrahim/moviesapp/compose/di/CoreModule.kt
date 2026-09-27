package me.ibrahim.moviesapp.compose.di

import me.ibrahim.moviesapp.compose.data.database.DatabaseFactory
import me.ibrahim.moviesapp.compose.data.database.MoviesDatabase
import me.ibrahim.moviesapp.compose.data.auth.KeystoreSessionStore
import me.ibrahim.moviesapp.compose.data.auth.SessionStore
import me.ibrahim.moviesapp.compose.presentation.cinema_selection.CinemaSelectionViewModel
import me.ibrahim.moviesapp.compose.presentation.time_selection.TimeSelectionViewModel
import me.ibrahim.moviesapp.compose.presentation.order_confirmation.OrderConfirmationViewModel
import me.ibrahim.moviesapp.compose.presentation.favorite.FavoriteMoviesViewModel
import me.ibrahim.moviesapp.compose.presentation.login.LoginViewModel
import me.ibrahim.moviesapp.compose.presentation.movies_detail.MovieDetailViewModel
import me.ibrahim.moviesapp.compose.presentation.movies_list.MoviesListViewModel
import me.ibrahim.moviesapp.compose.presentation.payment.PaymentViewModel
import me.ibrahim.moviesapp.compose.presentation.payment.PaymentAccountStore
import me.ibrahim.moviesapp.compose.presentation.seat_selection.SeatSelectionViewModel
import me.ibrahim.moviesapp.compose.presentation.settings.SettingsViewModel
import me.ibrahim.moviesapp.compose.presentation.settings.AndroidProfileImageStore
import me.ibrahim.moviesapp.compose.presentation.settings.ProfileImageStore
import me.ibrahim.moviesapp.compose.presentation.splash.SplashViewModel
import me.ibrahim.moviesapp.compose.presentation.recommendation.RecommendationViewModel
import me.ibrahim.moviesapp.compose.presentation.orders.OrdersViewModel
import me.ibrahim.moviesapp.compose.presentation.orders.OrderDetailViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val coreModule = module {
    viewModelOf(::MoviesListViewModel)
    viewModelOf(::MovieDetailViewModel)
    viewModelOf(::FavoriteMoviesViewModel)
    viewModelOf(::SeatSelectionViewModel)
    viewModelOf(::CinemaSelectionViewModel)
    viewModelOf(::TimeSelectionViewModel)
    viewModelOf(::OrderConfirmationViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::PaymentViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::SplashViewModel)
    viewModelOf(::RecommendationViewModel)
    viewModelOf(::OrdersViewModel)
    viewModelOf(::OrderDetailViewModel)

    single { DatabaseFactory.create(get()) }
    single { get<MoviesDatabase>().moviesDao }
    single { get<MoviesDatabase>().favoriteDao }
    single<SessionStore> { KeystoreSessionStore(get()) }
    single<ProfileImageStore> { AndroidProfileImageStore(get()) }
    single { PaymentAccountStore(get()) }
}

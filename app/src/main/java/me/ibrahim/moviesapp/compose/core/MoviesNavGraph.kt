package me.ibrahim.moviesapp.compose.core

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.ibrahim.moviesapp.compose.domain.Movie
import me.ibrahim.moviesapp.compose.presentation.cinema_selection.CinemaSelectionScreen
import me.ibrahim.moviesapp.compose.presentation.favorite.FavoriteMoviesScreen
import me.ibrahim.moviesapp.compose.presentation.login.LoginActivity
import me.ibrahim.moviesapp.compose.presentation.movies_detail.MovieDetailActivity
import me.ibrahim.moviesapp.compose.presentation.movies_detail.MovieDetailScreen
import me.ibrahim.moviesapp.compose.presentation.movies_list.MoviesListScreen
import me.ibrahim.moviesapp.compose.presentation.movies_list.MoviesListViewModel
import me.ibrahim.moviesapp.compose.presentation.payment.PaymentScreen
import me.ibrahim.moviesapp.compose.presentation.payment.PaymentViewModel
import me.ibrahim.moviesapp.compose.presentation.order_confirmation.OrderConfirmationScreen
import me.ibrahim.moviesapp.compose.presentation.orders.OrdersScreen
import me.ibrahim.moviesapp.compose.presentation.orders.OrderDetailScreen
import me.ibrahim.moviesapp.compose.presentation.search.SearchMoviesScreen
import me.ibrahim.moviesapp.compose.presentation.seat_selection.SeatSelectionScreen
import me.ibrahim.moviesapp.compose.presentation.seat_selection.SeatSelectionViewModel
import me.ibrahim.moviesapp.compose.presentation.settings.SettingsScreen
import me.ibrahim.moviesapp.compose.presentation.settings.SettingsViewModel
import me.ibrahim.moviesapp.compose.presentation.time_selection.TimeSelectionScreen
import org.koin.androidx.compose.koinViewModel
import kotlin.reflect.typeOf

@Composable
fun MoviesNavGraph(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    NavHost(navController = navController, startDestination = MoviesListRoute, modifier = modifier) {
        composable<MoviesListRoute> {
            val viewModel: MoviesListViewModel = koinViewModel()
            MoviesListScreen(viewModel = viewModel, onMovieClick = { openDetailActivity(context, it) })
        }
        composable<MovieDetailRoute>(typeMap = mapOf(typeOf<Movie>() to CustomNavType.MovieType)) {
            val movie = it.toRoute<MovieDetailRoute>().movie
            MovieDetailScreen(
                movie = movie,
                onBack = { navController.navigateUp() },
                onLoginRequired = { context.startActivity(Intent(context, LoginActivity::class.java).apply { putExtra(LoginActivity.EXTRA_SOURCE, "movie_detail") }) },
                onBuyTicketClick = { selectedMovie -> navController.navigate(CinemaSelectionRoute(selectedMovie.id, selectedMovie.title.orEmpty())) }
            )
        }
        composable<CinemaSelectionRoute> {
            val route = it.toRoute<CinemaSelectionRoute>()
            CinemaSelectionScreen(route.movieId, route.movieTitle, onBack = { navController.navigateUp() }) { cinemaId, localDate ->
                navController.navigate(TimeSelectionRoute(route.movieId, cinemaId, localDate))
            }
        }
        composable<TimeSelectionRoute> {
            val route = it.toRoute<TimeSelectionRoute>()
            TimeSelectionScreen(
                movieId = route.movieId, cinemaId = route.cinemaId, localDate = route.localDate,
                onBack = { navController.navigateUp() },
                onLoginRequired = { context.startActivity(Intent(context, LoginActivity::class.java).apply { putExtra(LoginActivity.EXTRA_SOURCE, "showtime") }) }
            ) { showtime ->
                navController.navigate(SeatSelectionRoute(route.movieId, showtime.cinemaId, showtime.auditoriumId, showtime.id, showtime.version, showtime.basePrice.amountMinor))
            }
        }
        composable<FavoriteMoviesRoute> { FavoriteMoviesScreen { openDetailActivity(context, it) } }
        composable<SearchMoviesRoute> { SearchMoviesScreen() }
        composable<SettingsRoute> { SettingsScreen(koinViewModel<SettingsViewModel>()) }
        composable<OrdersRoute> { OrdersScreen(onOrderClick = { navController.navigate(OrderDetailRoute(it)) }, onContinuePayment = { navController.navigate(PaymentRoute(orderId = it)) }) }
        composable<OrderDetailRoute> { OrderDetailScreen(it.toRoute<OrderDetailRoute>().orderId, onBack = { navController.navigateUp() }) }
        composable<SeatSelectionRoute> {
            val route = it.toRoute<SeatSelectionRoute>()
            val viewModel: SeatSelectionViewModel = koinViewModel()
            LaunchedEffect(route.showtimeId, route.latestVersion, route.priceMinor) { viewModel.setTicketPrice((route.priceMinor / 100.0).toString()) }
            SeatSelectionScreen(
                movieId = route.movieId, cinemaName = route.cinemaId, showTime = route.showtimeId,
                hallName = route.auditoriumId, viewModel = viewModel, onBack = { navController.navigateUp() },
                onConfirmSeats = { _, lockAndSeats ->
                navController.navigate(PaymentRoute(lockAndSeats.substringBefore('|')))
                },
                onContinuePayment = { lockId -> navController.navigate(PaymentRoute(lockId = lockId)) }
            )
        }
        composable<PaymentRoute> {
            val route = it.toRoute<PaymentRoute>()
            if (route.orderId == null && route.lockId.isNotBlank()) {
                // A successful seat lock is the hand-off point to payment. The
                // payment screen obtains the authoritative quote and creates
                // the pending order before showing payment methods.
                PaymentScreen(lockId = route.lockId, onBack = { navController.navigate(MoviesListRoute) { popUpTo<MoviesListRoute> { inclusive = false }; launchSingleTop = true; restoreState = true } }, onPaymentSuccess = { navController.navigate(OrderDetailRoute(it)) })
            } else {
                PaymentScreen(orderId = route.orderId, onBack = { navController.navigate(MoviesListRoute) { popUpTo<MoviesListRoute> { inclusive = false }; launchSingleTop = true; restoreState = true } }, onPaymentSuccess = { navController.navigate(OrderDetailRoute(it)) })
            }
        }
    }
}

fun openDetailActivity(context: Context, movie: Movie) {
    context.startActivity(Intent(context, MovieDetailActivity::class.java).apply { putExtra("movie", Json.encodeToString(movie)) })
}

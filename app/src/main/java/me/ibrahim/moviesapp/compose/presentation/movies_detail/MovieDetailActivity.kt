package me.ibrahim.moviesapp.compose.presentation.movies_detail

import androidx.compose.runtime.*
import android.content.Intent
import kotlinx.serialization.json.Json
import me.ibrahim.moviesapp.compose.core.BaseActivity
import me.ibrahim.moviesapp.compose.domain.Movie
import me.ibrahim.moviesapp.compose.domain.cinema.Showtime
import me.ibrahim.moviesapp.compose.presentation.cinema_selection.CinemaSelectionScreen
import me.ibrahim.moviesapp.compose.presentation.login.LoginActivity
import me.ibrahim.moviesapp.compose.presentation.payment.PaymentScreen
import me.ibrahim.moviesapp.compose.presentation.seat_selection.SeatSelectionScreen
import me.ibrahim.moviesapp.compose.presentation.seat_selection.SeatSelectionViewModel
import me.ibrahim.moviesapp.compose.presentation.time_selection.TimeSelectionScreen
import org.koin.androidx.compose.koinViewModel

class MovieDetailActivity : BaseActivity() {
    enum class DetailScreenState { DETAIL, CINEMA_SELECTION, TIME_SELECTION, SEAT_SELECTION, PAYMENT }
    @Composable override fun InitView() {
        intent.getStringExtra("movie")?.let { value -> render(Json.decodeFromString<Movie>(value)) }
    }
    @Composable private fun render(movie:Movie) {
        var current by remember { mutableStateOf(DetailScreenState.DETAIL) }
        var cinemaId by remember { mutableStateOf("") }; var date by remember { mutableStateOf("") }
        var cinemaName by remember { mutableStateOf("") }; var showtime by remember { mutableStateOf("") }
        var showtimeId by remember { mutableStateOf("") }
        var hall by remember { mutableStateOf("") }; var lockId by remember { mutableStateOf("") }; var orderId by remember { mutableStateOf<String?>(null) }; var price by remember { mutableStateOf("0.0") }
        when(current){
            DetailScreenState.DETAIL -> MovieDetailScreen(movie=movie,onBack={finish()},onLoginRequired={startActivity(Intent(this,LoginActivity::class.java).apply{putExtra(LoginActivity.EXTRA_SOURCE,"movie_detail")})},onBuyTicketClick={current=DetailScreenState.CINEMA_SELECTION})
            DetailScreenState.CINEMA_SELECTION -> CinemaSelectionScreen(movieId=movie.id,movieTitle=movie.title.orEmpty(),onBack={current=DetailScreenState.DETAIL}){id,d->cinemaId=id;date=d;cinemaName=id;current=DetailScreenState.TIME_SELECTION}
            DetailScreenState.TIME_SELECTION -> TimeSelectionScreen(movieId=movie.id,cinemaId=cinemaId,localDate=date,onBack={current=DetailScreenState.CINEMA_SELECTION},onLoginRequired={startActivity(Intent(this,LoginActivity::class.java).apply{putExtra(LoginActivity.EXTRA_SOURCE,"showtime")})}){s:Showtime->showtimeId=s.id;showtime=s.startsAt;hall=s.auditoriumName;price=(s.basePrice.amountMinor/100.0).toString();current=DetailScreenState.SEAT_SELECTION}
            DetailScreenState.SEAT_SELECTION -> {
                val vm: SeatSelectionViewModel = koinViewModel()
                SeatSelectionScreen(
                    movieId = movie.id,
                    cinemaName = cinemaName,
                    showTime = showtimeId,
                    hallName = hall,
                    onBack = { current = DetailScreenState.TIME_SELECTION },
                    onConfirmSeats = { _, id ->
                        lockId = id
                        orderId = null
                        current = DetailScreenState.PAYMENT
                    },
                    viewModel = vm,
                    showTimeLabel = showtime,
                    onContinuePayment = { id ->
                        lockId = id
                        orderId = null
                        current = DetailScreenState.PAYMENT
                    },
                )
            }
            DetailScreenState.PAYMENT -> PaymentScreen(movieId=movie.id,price=price,lockId=if (orderId == null) lockId else null,orderId=orderId,onBack={ finish() },onPaymentSuccess={ finish() })
        }
    }
}

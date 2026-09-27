package me.ibrahim.moviesapp.compose.presentation.time_selection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.domain.cinema.*
import me.ibrahim.moviesapp.compose.presentation.auth.AuthRequiredDialog
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSelectionScreen(movieId: Int, cinemaId: String, localDate: String, viewModel: TimeSelectionViewModel = koinViewModel(), onBack: () -> Unit, onLoginRequired: () -> Unit = {}, onTimeSelected: (Showtime) -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(movieId, cinemaId, localDate) { viewModel.start(movieId, cinemaId, localDate) }
    LaunchedEffect(viewModel) { viewModel.effects.collect { if (it is TimeSelectionEffect.Ready) onTimeSelected(it.showtime) } }
    Box(Modifier.fillMaxSize().background(Color(0xFF19191B))) {
    Image(painterResource(R.drawable.bg1), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(title = { Column { Text(state.cinema?.name ?: stringResource(R.string.showtime_choose), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold); Text(localDate, style = MaterialTheme.typography.bodySmall, color = Color.LightGray) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.notice?.let { Text(stringResource(if (it == "SHOWTIME_CHANGED") R.string.showtime_changed else if (it == "SHOWTIME_UNAVAILABLE") R.string.showtime_unavailable else R.string.cinema_error), color = Color(0xFF9A6700), modifier = Modifier.padding(16.dp)) }
            when (state.loadState) {
                TimeLoadState.LOADING -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color(0xFF5068A3)) }
                TimeLoadState.ERROR -> TimeStatus(stringResource(R.string.cinema_error), viewModel::retry)
                TimeLoadState.EMPTY -> TimeStatus(stringResource(R.string.showtime_empty), viewModel::retry)
                TimeLoadState.CONTENT -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(state.cinema?.showtimes.orEmpty(), key = { it.id }) { show -> ShowtimeCard(show, state.validatingId == show.id) { viewModel.select(show) } } }
            }
        }
    }
    }
    if (state.authRequired) AuthRequiredDialog(onDismiss = viewModel::dismissAuth, onLogin = { viewModel.dismissAuth(); onLoginRequired() })
}

@Composable private fun ShowtimeCard(show: Showtime, validating: Boolean, onClick: () -> Unit) {
    val enabled = show.selectable && !validating
    Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = "${localClock(show.startsAt, show.timeZone)} ${show.auditoriumName} ${show.basePrice.format()}"; if (!enabled) disabled() }, colors = CardDefaults.cardColors(containerColor = Color(0xFF444445)), border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)), shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccessTime, null, tint = if (enabled) Color(0xFFFFD700) else Color.Gray, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)); Column(Modifier.width(72.dp)) { Text(localClock(show.startsAt, show.timeZone), style = MaterialTheme.typography.titleLarge, color = Color.White); Text(localClock(show.endsAt, show.timeZone) + stringResource(R.string.showtime_ends), style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("${show.language} · ${show.format}", color = Color.White); Text(show.auditoriumName, style = MaterialTheme.typography.bodySmall, color = Color.LightGray); show.unavailableReason?.let { Text(reasonText(it), color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall) } }
            Column(horizontalAlignment = Alignment.End) { Text(show.basePrice.format(), color = Color(0xFFFFD700), style = MaterialTheme.typography.titleMedium); Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black, disabledContainerColor = Color(0xFF424242), disabledContentColor = Color.Gray)) { if (validating) CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black) else Text(stringResource(R.string.showtime_select)) } }
        }
    }
}

@Composable private fun TimeStatus(message: String, retry: () -> Unit) = Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(message, color = Color.White); Spacer(Modifier.height(12.dp)); Button(onClick = retry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black)) { Text(stringResource(R.string.retry)) } } }
@Composable private fun reasonText(reason: UnavailableReason) = stringResource(when (reason) { UnavailableReason.STARTED -> R.string.showtime_started; UnavailableReason.ENDED -> R.string.showtime_ended; UnavailableReason.CANCELLED -> R.string.showtime_cancelled; UnavailableReason.SALES_STOPPED -> R.string.showtime_stopped; UnavailableReason.SOLD_OUT -> R.string.showtime_sold_out; UnavailableReason.CINEMA_CLOSED -> R.string.showtime_cinema_closed; UnavailableReason.AUDITORIUM_INACTIVE -> R.string.showtime_auditorium_inactive })
private fun localClock(instant: String, zone: String): String = runCatching { val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US); val value = parser.parse(instant.replace("Z", "+00:00")) ?: return@runCatching "--:--"; SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(zone) }.format(value) }.getOrDefault("--:--")

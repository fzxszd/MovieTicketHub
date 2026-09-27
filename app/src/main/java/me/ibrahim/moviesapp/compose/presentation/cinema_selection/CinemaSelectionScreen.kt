package me.ibrahim.moviesapp.compose.presentation.cinema_selection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.domain.cinema.*
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinemaSelectionScreen(movieId: Int, movieTitle: String, viewModel: CinemaSelectionViewModel = koinViewModel(), onBack: () -> Unit, onCinemaSelected: (String, String) -> Unit) {
    val state by viewModel.state.collectAsState(); var filtersVisible by remember { mutableStateOf(false) }; var sortVisible by remember { mutableStateOf(false) }
    LaunchedEffect(movieId) { viewModel.start(movieId, movieTitle) }; LaunchedEffect(viewModel) { viewModel.effects.collect { if (it is CinemaSelectionEffect.OpenCinema) onCinemaSelected(it.cinemaId, it.localDate) } }
    Box(Modifier.fillMaxSize().background(Color(0xFF19191B))) {
        Image(painter = painterResource(R.drawable.bg1), contentDescription = null, modifier = Modifier.fillMaxSize())
        Scaffold(containerColor = Color.Transparent, topBar = { TopAppBar(title = { Column { Text(stringResource(R.string.cinema_choose), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold); Text(movieTitle, style = MaterialTheme.typography.bodySmall, color = Color.LightGray) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White, navigationIconContentColor = Color.White)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { state.dates.forEach { date -> FilterChip(selected = date == state.selectedDate, onClick = { viewModel.selectDate(date) }, label = { Text(date) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF5068A3), selectedLabelColor = Color.White, containerColor = Color(0xFF282F32), labelColor = Color.White)) } }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) { OutlinedButton(onClick = { filtersVisible = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = BorderStroke(1.dp, Color.White.copy(alpha = .35f))) { Icon(Icons.Default.FilterList, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.cinema_filters)) }; Box { OutlinedButton(onClick = { sortVisible = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = BorderStroke(1.dp, Color.White.copy(alpha = .35f))) { Text(sortLabel(state.sort)) }; DropdownMenu(expanded = sortVisible, onDismissRequest = { sortVisible = false }) { SortType.entries.forEach { sort -> DropdownMenuItem(text = { Text(sortLabel(sort)) }, onClick = { viewModel.changeSort(sort); sortVisible = false }) } } } }
            if (state.sortNotice == SortNotice.LOCATION_UNAVAILABLE) Text(stringResource(R.string.cinema_location_unavailable), color = Color(0xFFFFD54F), modifier = Modifier.padding(16.dp))
            when (state.loadState) { CinemaLoadState.LOADING -> LoadingPane(); CinemaLoadState.ERROR -> StatusPane(stringResource(R.string.cinema_error), viewModel::retry); CinemaLoadState.EMPTY -> StatusPane(stringResource(R.string.cinema_empty), viewModel::retry); CinemaLoadState.CONTENT -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(state.cinemas, key = { it.id }) { cinema -> CinemaCard(cinema) { viewModel.selectCinema(cinema) } } }; CinemaLoadState.IDLE -> Unit }
        }
        }
    }; if (filtersVisible) FilterDialog(state, viewModel) { filtersVisible = false }
}

@Composable private fun LoadingPane() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF5068A3)) }
@Composable private fun CinemaCard(cinema: Cinema, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color(0xFF444445)), shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Movie, null, tint = Color(0xFFFFD700), modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)); Text(cinema.name, style = MaterialTheme.typography.titleMedium, color = Color.White) }; Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(cinema.address, style = MaterialTheme.typography.bodySmall, color = Color.LightGray) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) { Column { Text(cinema.minimumPrice.format() + stringResource(R.string.cinema_price_from), color = Color(0xFFFFD700), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.cinema_showtime_count, cinema.showtimes.size), style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; Text(cinema.distanceMeters?.let { "%.1f km".format(it / 1000.0) } ?: stringResource(R.string.cinema_distance_unknown), color = Color.Gray, style = MaterialTheme.typography.bodySmall) } } } }
@Composable private fun StatusPane(message: String, retry: () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(message, color = Color(0xFF24232A)); Spacer(Modifier.height(12.dp)); Button(onClick = retry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5068A3), contentColor = Color.White)) { Text(stringResource(R.string.retry)) } } }
@Composable private fun sortLabel(sort: SortType): String = stringResource(when (sort) { SortType.RECOMMENDED -> R.string.cinema_sort_recommended; SortType.DISTANCE -> R.string.cinema_sort_distance; SortType.PRICE -> R.string.cinema_sort_price })
@Composable private fun FilterDialog(state: CinemaSelectionState, viewModel: CinemaSelectionViewModel, dismiss: () -> Unit) { var draft by remember(state.filterDraft) { mutableStateOf(state.filterDraft) }; AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.cinema_filters)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.cinema_district)); state.availableDistricts.forEach { district -> FilterChip(district in draft.districts, { draft = draft.copy(districts = if (district in draft.districts) draft.districts - district else draft.districts + district) }, { Text(district) }) }; OutlinedTextField(draft.startTime.orEmpty(), { draft = draft.copy(startTime = it.ifBlank { null }) }, label = { Text(stringResource(R.string.cinema_start_time)) }, singleLine = true); OutlinedTextField(draft.endTime.orEmpty(), { draft = draft.copy(endTime = it.ifBlank { null }) }, label = { Text(stringResource(R.string.cinema_end_time)) }, singleLine = true); OutlinedTextField(draft.maxPriceMinor?.div(100)?.toString().orEmpty(), { draft = draft.copy(maxPriceMinor = it.toLongOrNull()?.times(100)) }, label = { Text(stringResource(R.string.cinema_max_price)) }, singleLine = true) } }, confirmButton = { TextButton(onClick = { viewModel.updateDraft(draft); viewModel.applyFilters(); dismiss() }) { Text(stringResource(R.string.apply)) } }, dismissButton = { Row { TextButton(onClick = { viewModel.clearFilters(); dismiss() }) { Text(stringResource(R.string.clear_filters)) }; TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } } }) }

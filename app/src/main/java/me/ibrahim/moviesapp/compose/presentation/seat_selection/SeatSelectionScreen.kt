package me.ibrahim.moviesapp.compose.presentation.seat_selection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import me.ibrahim.moviesapp.compose.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatSelectionScreen(movieId: Int, cinemaName: String, showTime: String, hallName: String, onBack: () -> Unit, onConfirmSeats: (Double, String) -> Unit, viewModel: SeatSelectionViewModel = koinViewModel(), showTimeLabel: String? = null, onContinuePayment: (String) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val displayCinemaName = localizedCinemaName(cinemaName)
    val displayShowTime = localizedShowTime(showTimeLabel ?: showTime)
    val displayHallName = localizedHallName(hallName)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(movieId, cinemaName, showTime, hallName) { viewModel.loadOccupiedSeats(movieId, cinemaName, showTime, hallName); viewModel.startPolling() }
    DisposableEffect(viewModel) { onDispose { viewModel.stopPolling() } }
    Box(Modifier.fillMaxSize().background(Color(0xFF19191B))) {
    Image(painterResource(R.drawable.bg1), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    Scaffold(containerColor = Color.Transparent, topBar = { TopAppBar(title = { Column { Text(displayCinemaName, color = Color.White); Text("$displayShowTime | $displayHallName", fontSize = 11.sp, color = Color.LightGray) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)) }) { padding ->
        CompositionLocalProvider(LocalContentColor provides Color.LightGray) {
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            state.activeLock?.let { AssistChip(onClick = { viewModel.revalidateActiveLock {} }, label = { Text("座位已锁定，还剩 ${state.remainingLockSeconds / 60}:${(state.remainingLockSeconds % 60).toString().padStart(2, '0')}") }) }
            state.error?.let { Text(if (it == "MAX_SEATS") "单笔订单最多选择 6 个座位" else "座位状态已变化或加载失败，请重试", color = MaterialTheme.colorScheme.error) }
            state.activeLock?.let { lock ->
                Button(onClick = { onContinuePayment(lock.id) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34E3C4), contentColor = Color.Black)) {
                    Text("继续支付")
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 3f); offset += pan; if (scale == 1f) offset = Offset.Zero } }) {
                Column(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y; transformOrigin = TransformOrigin.Center }, horizontalAlignment = Alignment.CenterHorizontally) {
                    ScreenIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("座位状态", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    SeatLegend()
                    Spacer(Modifier.height(8.dp))
                    val seatsById = state.seats.associateBy { it.id }
                    state.layout?.rows?.forEach { row -> Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(row.rowLabel, modifier = Modifier.width(24.dp), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        row.positions.sortedBy { it.columnIndex }.forEach { p -> val seat = p.seatId?.let(seatsById::get); when (p.positionType.name) { "AISLE", "EMPTY" -> Spacer(Modifier.size(35.dp)); else -> if (seat == null) Spacer(Modifier.size(35.dp)) else SeatItem(seat, p.priceZoneId) { viewModel.onSeatClick(seat.id) } } }
                    } }
                    if (state.loading) CircularProgressIndicator()
                }
            }
            SeatLegend()
            if (state.selectedCount > 0) { val labels = state.seats.filter { it.id in state.selectedIds }.joinToString(", ") { "${it.rowLabel}${it.seatLabel}" }; Text("已选座位：$labels"); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("共 ${state.selectedCount} 张票"); Text("合计：%.2f".format(state.totalPrice), fontWeight = FontWeight.Bold) } }
            Button(onClick = { viewModel.lockSelected { lock -> onConfirmSeats(lock.totalPrice.amountMinor / 100.0, lock.id) } }, enabled = state.selectedCount > 0 && !state.locking, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34E3C4), contentColor = Color.Black)) { Text(if (state.locking) "正在锁定座位…" else "确认锁定座位") }
        }
        }
    }
    }
}

@Composable private fun ScreenIndicator() = Column(horizontalAlignment = Alignment.CenterHorizontally) { Canvas(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 40.dp)) { drawPath(Path().apply { moveTo(0f, size.height); quadraticTo(size.width / 2, 0f, size.width, size.height) }, Color.Gray, style = Stroke(2.dp.toPx())) }; Text("屏幕方向", fontSize = 12.sp) }
@Composable private fun SeatItem(seat: Seat, priceZone: String?, onClick: () -> Unit) { val color = when (seat.status) { SeatStatus.AVAILABLE -> Color.Gray; SeatStatus.SELECTED -> Color(0xFFFFC107); SeatStatus.RESERVED -> Color.DarkGray; SeatStatus.LOCKED_BY_ME -> Color.Cyan; SeatStatus.UNAVAILABLE -> Color.Red }; val stateLabel = when (seat.status) { SeatStatus.AVAILABLE -> "可选"; SeatStatus.SELECTED -> "已选择"; SeatStatus.RESERVED -> "已售或被其他用户锁定"; SeatStatus.LOCKED_BY_ME -> "已被我锁定"; SeatStatus.UNAVAILABLE -> "不可用" }; Icon(Icons.Default.EventSeat, null, tint = color, modifier = Modifier.size(35.dp).semantics { contentDescription = "${seat.rowLabel}排 ${seat.seatLabel ?: seat.column}座，$stateLabel，${seat.priceMinor / 100.0}元，${priceZone ?: "标准"}区域" }.clickable(enabled = seat.status == SeatStatus.AVAILABLE || seat.status == SeatStatus.SELECTED, onClick = onClick)) }
@Composable private fun SeatLegend() = Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) { Legend("可选", Color.Gray); Legend("已选择", Color(0xFFFFC107)); Legend("他人锁定/已售", Color.DarkGray); Legend("不可用", Color.Red) }
@Composable private fun Legend(label: String, color: Color) = Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp))); Spacer(Modifier.width(3.dp)); Text(label, fontSize = 10.sp) }
private fun localizedCinemaName(value: String): String = when (value) {
    "cin_sh_001" -> "浦东星光影城"
    else -> value.removePrefix("cin_").replace('_', ' ').ifBlank { "电影院" }
}

private fun localizedHallName(value: String): String = when (value) {
    "aud_001", "aud_remote_001" -> "1号激光厅"
    else -> value.removePrefix("aud_").replace('_', ' ').ifBlank { "放映厅" }
}

private fun localizedShowTime(value: String): String {
    val parsed = runCatching {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val date = input.parse(value.replace("Z", "+00:00")) ?: return@runCatching null
        SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA).apply { timeZone = TimeZone.getDefault() }.format(date)
    }.getOrNull()
    return parsed ?: if (value.startsWith("st_")) "场次时间" else value
}

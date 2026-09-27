package me.ibrahim.moviesapp.compose.presentation.order_confirmation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.domain.order.ServerOrder
import org.koin.androidx.compose.koinViewModel

@Composable
fun OrderConfirmationScreen(lockId: String, onBack: () -> Unit, onCreated: (ServerOrder) -> Unit = {}, viewModel: OrderConfirmationViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(lockId) { viewModel.load(lockId) }
    LaunchedEffect(state.order?.id) { state.order?.let(onCreated) }
    Box(Modifier.fillMaxSize().background(Color(0xFF19191B))) {
        Image(painterResource(R.drawable.bg1), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("订单确认", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            when {
                state.loading -> CircularProgressIndicator(color = Color(0xFF34E3C4))
                state.quote != null -> {
                    val quote = state.quote!!
                    Text(quote.movie.title.orEmpty(), color = Color.White)
                    Text("${quote.cinema.name} · ${quote.auditorium.name}", color = Color.LightGray)
                    quote.items.forEach { Text("${it.rowLabel}${it.seatLabel}  ${it.unitPrice.amountMinor / 100.0} ${it.unitPrice.currency}", color = Color.White) }
                    Text("合计 ${quote.total.amountMinor / 100.0} ${quote.total.currency}", color = Color(0xFFFFD700))
                    if (state.amountChanged) Text("金额已变化，请重新核对", color = Color(0xFFFF8A80))
                    Button(enabled = !state.creating, onClick = viewModel::confirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34E3C4), contentColor = Color.Black)) { if (state.creating) CircularProgressIndicator() else Text("确认并创建订单") }
                }
                state.error != null -> Text("订单不可用：${state.error}", color = Color(0xFFFF8A80))
            }
            OutlinedButton(onClick = onBack, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34E3C4))) { Text("返回") }
        }
    }
}

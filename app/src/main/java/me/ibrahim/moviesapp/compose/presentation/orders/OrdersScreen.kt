package me.ibrahim.moviesapp.compose.presentation.orders

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import me.ibrahim.moviesapp.compose.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun OrdersScreen(viewModel: OrdersViewModel = koinViewModel(), onOrderClick: (String) -> Unit = {}, onContinuePayment: (String) -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    Box(Modifier.fillMaxSize().background(Color(0xFF19191B))) {
        Image(painterResource(R.drawable.bg1), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("我的订单", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            when {
                state.loading -> CircularProgressIndicator(color = Color(0xFF34E3C4))
                state.error != null -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text("加载失败", color = Color(0xFFFF8A80)); Button(onClick = viewModel::refresh) { Text("重试") } }
                state.orders.isEmpty() -> Text("暂无订单", color = Color.Gray)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.orders, key = { it.id }) { order ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF444445)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("订单 ${order.id}", color = Color.White)
                                Text("${order.status} · ${order.totalMinor / 100.0} ${order.currency}", color = Color.LightGray)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (order.status == "PENDING_PAYMENT" || order.status == "PAYMENT_PROCESSING") TextButton(onClick = { onContinuePayment(order.id) }) { Text("继续支付", color = Color(0xFF34E3C4)) }
                                    TextButton(onClick = { onOrderClick(order.id) }) { Text("详情", color = Color(0xFFFFD700)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

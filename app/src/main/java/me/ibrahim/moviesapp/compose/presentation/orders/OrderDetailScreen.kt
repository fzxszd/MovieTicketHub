package me.ibrahim.moviesapp.compose.presentation.orders

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
import org.koin.androidx.compose.koinViewModel

@Composable
fun OrderDetailScreen(orderId: String, onBack: () -> Unit, viewModel: OrderDetailViewModel = koinViewModel()) {
    val order by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()
    LaunchedEffect(orderId) { viewModel.load(orderId) }
    Box(Modifier.fillMaxSize().background(Color(0xFF19191B))) {
        Image(painterResource(R.drawable.bg1), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("订单详情", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            error?.let { Text("无法加载：$it", color = Color(0xFFFF8A80)) }
            order?.let {
                Text("订单状态：${it.status}", color = Color.White)
                Text("订单金额：${it.totalMinor / 100.0} ${it.currency}", color = Color.White)
                if (it.status == "PAID") Text("电影票：${it.ticket?.credential ?: "生成中"}", color = Color(0xFF34E3C4))
            }
            OutlinedButton(onClick = onBack, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34E3C4))) { Text("返回") }
        }
    }
}

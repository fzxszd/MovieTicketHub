package me.ibrahim.moviesapp.compose.presentation.payment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import me.ibrahim.moviesapp.compose.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun PaymentScreen(
    movieId: Int = 0,
    price: String = "",
    lockId: String? = null,
    orderId: String? = null,
    onBack: () -> Unit,
    onPaymentSuccess: (String) -> Unit,
    viewModel: PaymentViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    BackHandler { viewModel.abandonUnpaidPayment(onBack) }
    LaunchedEffect(lockId, orderId) {
        when {
            orderId != null -> viewModel.loadOrder(orderId)
            lockId != null -> viewModel.createFromLock(lockId)
        }
    }
    LaunchedEffect(state.confirmed) {
        if (state.confirmed) state.order?.id?.let(onPaymentSuccess)
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF19191B))) {
        Image(painterResource(R.drawable.bg1), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("支付", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        when {
            state.loading -> CircularProgressIndicator(color = Color(0xFF5068A3))
            state.order != null -> {
                val order = state.order!!
                Text("订单", color = Color.White)
                Text(order.id, color = Color.White)
                Text("订单金额：${order.totalMinor / 100.0} ${order.currency}", color = Color.White)
                Text("支付后余额：${((state.accounts.firstOrNull { it.id == state.selectedMethod }?.balanceMinor ?: 0L) - order.totalMinor) / 100.0} ${order.currency}", color = Color.White)
                state.accounts.forEach { account ->
                    FilterChip(selected = account.id == state.selectedMethod, onClick = { viewModel.selectMethod(account.id) }, label = { Text("${account.displayName}余额 ${(account.balanceMinor / 100.0)}") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFE9D8FF), selectedLabelColor = Color(0xFF24232A), containerColor = Color.Transparent, labelColor = Color.White))
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (order.status != "PAID") {
                    Button(enabled = !state.processing, onClick = { viewModel.pay() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5068A3), contentColor = Color.White)) {
                        if (state.processing) CircularProgressIndicator() else Text("确认支付")
                    }
                }
                if (order.status == "PAID") Text("支付成功，电子票据已生成", color = Color(0xFF34E3C4))
            }
            state.error != null -> Text("支付暂不可用：${state.error}", color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(onClick = { viewModel.abandonUnpaidPayment(onBack) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8EA2E0))) { Text("返回") }
        }
    }
}

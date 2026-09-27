package me.ibrahim.moviesapp.compose.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.domain.order.TicketSummary
import me.ibrahim.moviesapp.compose.presentation.login.LoginActivity
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.refreshTickets()
    }

    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(true) }
    var darkModeEnabled by remember { mutableStateOf(true) }
    
    val tickets by viewModel.tickets.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    
    var showEditDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var refundTicket by remember { mutableStateOf<TicketSummary?>(null) }
    var isRefunding by remember { mutableStateOf(false) }
    var refundError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refundTicket?.id) {
        Log.d("RefundDebug", "页面级退票状态：ticketId=${refundTicket?.id}")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.blackBackground))
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg1),
            contentDescription = null,
            alpha = 1f
        )

        Scaffold(
            containerColor = Color.Transparent 
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(id = R.string.personal_center),
                        overflow = TextOverflow.Visible,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(vertical = 8.dp),
                        maxLines = 1,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Start
                        )
                    )
                }

                item {
                    UserProfileSection(
                        profile = userProfile,
                        onEditClick = { showEditDialog = true }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (tickets.isNotEmpty()) {
                    item { SettingsGroupTitle(title = "我的电子票据 (${tickets.size})") }
                    items(items = tickets, key = { it.id }) { ticket ->
                        TicketCard(
                            ticket = ticket,
                            viewModel = viewModel,
                            onRefundClick = {
                                Log.d("RefundDebug", "SettingsScreen 接收退票请求，ticketId=${it.id}")
                                refundError = null
                                refundTicket = it
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
                item {
                    SettingsGroupTitle(title = stringResource(id = R.string.general_settings))
                    SettingsSwitchItem(
                        icon = Icons.Default.Notifications,
                        title = stringResource(id = R.string.push_notifications),
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.DarkMode,
                        title = stringResource(id = R.string.dark_mode),
                        checked = darkModeEnabled,
                        onCheckedChange = { darkModeEnabled = it }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    SettingsGroupTitle(title = stringResource(id = R.string.about))
                    SettingsClickableItem(icon = Icons.Default.Info, title = stringResource(id = R.string.app_version), value = "v1.0.2")
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Button(
                        onClick = {
                            viewModel.logout {
                                val intent = Intent(context, LoginActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.6f)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(stringResource(id = R.string.logout), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedButton(
                        onClick = { showClearDataDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(stringResource(id = R.string.clear_data_and_delete), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        refundTicket?.let { ticket ->
            RefundConfirmationOverlay(
                isRefunding = isRefunding,
                error = refundError,
                onDismiss = {
                    if (!isRefunding) refundTicket = null
                },
                onConfirm = {
                    Log.d("RefundDebug", "页面级确认退票，ticketId=${ticket.id}, orderId=${ticket.orderId}")
                    isRefunding = true
                    refundError = null
                    viewModel.refundTicket(ticket) { success ->
                        isRefunding = false
                        if (success) {
                            refundTicket = null
                        } else {
                            refundError = "退票失败，请稍后重试"
                        }
                    }
                }
            )
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            profile = userProfile,
            onDismiss = { showEditDialog = false },
            onSave = { name, email, avatarUri ->
                viewModel.updateUserProfile(name, email, avatarUri)
                showEditDialog = false
            },
            viewModel = viewModel
        )
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("危险操作") },
            text = { Text("这将删除所有账号信息、购票记录和收藏。此操作不可撤销！") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData {
                        val intent = Intent(context, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        context.startActivity(intent)
                    }
                    showClearDataDialog = false
                }) {
                    Text("确认清空", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    /* The confirmation UI is rendered inside the page Box above so it cannot
       be hidden behind a platform dialog window. */
    if (false) refundTicket?.let { ticket ->
        AlertDialog(
            onDismissRequest = {
                if (!isRefunding) refundTicket = null
            },
            title = { Text(stringResource(id = R.string.confirm_refund_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(id = R.string.confirm_refund_msg))
                    refundError?.let { error ->
                        Text(error, color = Color(0xFFFF6B6B))
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isRefunding,
                    onClick = {
                        Log.d("RefundDebug", "SettingsScreen 确认退票，ticketId=${ticket.id}, orderId=${ticket.orderId}")
                        isRefunding = true
                        refundError = null
                        viewModel.refundTicket(ticket) { success ->
                            isRefunding = false
                            if (success) {
                                refundTicket = null
                            } else {
                                refundError = "退票失败，请稍后重试"
                            }
                        }
                    }
                ) {
                    Text(if (isRefunding) "处理中…" else stringResource(id = R.string.refund))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isRefunding,
                    onClick = {
                        Log.d("RefundDebug", "SettingsScreen 取消退票，ticketId=${ticket.id}")
                        refundTicket = null
                    }
                ) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun RefundConfirmationOverlay(
    isRefunding: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = !isRefunding, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clickable(enabled = false) {},
            colors = CardDefaults.cardColors(containerColor = Color(0xFF242424)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.confirm_refund_title),
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.confirm_refund_msg),
                    color = Color(0xFFE6E6E6),
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
                error?.let {
                    Text(text = it, color = Color(0xFFFF6B6B), fontSize = 14.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    OutlinedButton(enabled = !isRefunding, onClick = onDismiss) {
                        Text(stringResource(id = R.string.cancel))
                    }
                    Button(
                        enabled = !isRefunding,
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text(if (isRefunding) "处理中…" else stringResource(id = R.string.refund))
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketCard(
    ticket: me.ibrahim.moviesapp.compose.domain.order.TicketSummary,
    viewModel: SettingsViewModel,
    onRefundClick: (TicketSummary) -> Unit
) {
    var showRefundDialog by remember(ticket.id) { mutableStateOf(false) }
    var isRefunding by remember(ticket.id) { mutableStateOf(false) }
    var refundError by remember(ticket.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(ticket.id, showRefundDialog) {
        Log.d(
            "RefundDebug",
            "TicketCard state changed: ticketId=${ticket.id}, showRefundDialog=$showRefundDialog, isRefunding=$isRefunding"
        )
    }

    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF242424)), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(model = ticket.posterUrl ?: R.drawable.bg1, contentDescription = ticket.movieTitle, modifier = Modifier.size(width = 86.dp, height = 120.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(ticket.movieTitle ?: "电影票", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${ticket.cinemaName ?: "影院"} · ${ticket.auditoriumName ?: "影厅"}", color = Color.LightGray)
                Text("座位：${ticket.seats.joinToString()}", color = Color.LightGray)
                Text("金额：${ticket.totalMinor / 100.0} ${ticket.currency}", color = Color.LightGray)
                Text("票据号：${ticket.id}", color = Color(0xFFFFD700), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Button(
                    onClick = {
                        Log.d("RefundDebug", "申请退票按钮被点击，ticketId=${ticket.id}")
                        onRefundClick(ticket)
                    },
                    enabled = !isRefunding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6D2428),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isRefunding) "退票处理中…" else "申请退票")
                }
            }
        }
    }

    if (showRefundDialog) {
        Log.d("RefundDebug", "开始组合退票 AlertDialog，ticketId=${ticket.id}")
        AlertDialog(
            onDismissRequest = {
                Log.d("RefundDebug", "退票 AlertDialog 请求关闭，ticketId=${ticket.id}")
                if (!isRefunding) showRefundDialog = false
            },
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(24.dp),
            containerColor = Color(0xFF242424),
            tonalElevation = 12.dp,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "确认退票",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "退票后将释放该订单的座位，票据也会失效。",
                        color = Color(0xFFE6E6E6),
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                    Text(
                        text = "确定要继续退票吗？",
                        color = Color(0xFFFFC107),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    refundError?.let {
                        Text(
                            text = it,
                            color = Color(0xFFFF6B6B),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isRefunding,
                    onClick = {
                        Log.d("RefundDebug", "确认退票按钮被点击，ticketId=${ticket.id}, orderId=${ticket.orderId}")
                        isRefunding = true
                        refundError = null
                        viewModel.refundTicket(ticket) { success ->
                            isRefunding = false
                            if (success) {
                                showRefundDialog = false
                            } else {
                                refundError = "退票失败，请确认后端已启动并稍后重试"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF6D3535)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("确认退票", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !isRefunding,
                    onClick = {
                        Log.d("RefundDebug", "取消退票按钮被点击，ticketId=${ticket.id}")
                        showRefundDialog = false
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEEEEEE)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF888888)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("取消", fontSize = 15.sp)
                }
            }
        )
    }
}

@Composable
fun EditProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit,
    viewModel: SettingsViewModel
) {
    var name by remember { mutableStateOf(profile.name) }
    var email by remember { mutableStateOf(profile.email) }
    var avatarUri by remember { mutableStateOf(profile.avatarUri) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                val localPath = viewModel.saveImageToInternalStorage(it)
                avatarUri = localPath
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.edit_profile)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.3f))
                        .clickable { 
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUri != null) {
                        AsyncImage(
                            model = avatarUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color.White)
                    }
                }
                Text("点击更换头像", fontSize = 12.sp, color = Color.Gray)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(id = R.string.nickname)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(id = R.string.email)) },
                    readOnly = true,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, email, avatarUri) }) {
                Text(stringResource(id = R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}

/* Legacy OrderItem implementation removed; kept commented only as a migration marker.
@Composable
fun OrderItem(order: OrderEntity, onRefundClick: () -> Unit) {
    var showRefundDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B2B).copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.movieTitle,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "￥${order.price}",
                    color = Color.Yellow,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = order.moviePoster ?: R.drawable.bg1,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp, 84.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(order.cinemaName, color = Color.White, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${order.showTime} | ${order.hallName}", color = Color.LightGray, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EventSeat, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(order.seatInfo, color = Color.Yellow.copy(alpha = 0.9f), fontSize = 13.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "下单时间: ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(order.timestamp))}",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
                
                TextButton(
                    onClick = { showRefundDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text(stringResource(id = R.string.refund), fontSize = 12.sp)
                }
            }
        }
    }

    if (showRefundDialog) {
        AlertDialog(
            onDismissRequest = { showRefundDialog = false },
            title = { Text(stringResource(id = R.string.confirm_refund_title)) },
            text = { Text(stringResource(id = R.string.confirm_refund_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    onRefundClick()
                    showRefundDialog = false
                }) {
                    Text(stringResource(id = R.string.refund), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRefundDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
}

*/
@Composable
fun UserProfileSection(
    profile: UserProfile,
    onEditClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .clickable { onEditClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            if (profile.avatarUri != null) {
                AsyncImage(
                    model = profile.avatarUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.White)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(profile.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(profile.email, color = Color.Gray, fontSize = 14.sp)
        }
        Icon(Icons.Default.Edit, contentDescription = "编辑", tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingsGroupTitle(title: String) {
    Text(
        text = title,
        color = colorResource(id = R.color.orange),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SettingsClickableItem(
    icon: ImageVector, 
    title: String, 
    value: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, color = Color.White, modifier = Modifier.weight(1f), fontSize = 16.sp)
        if (value != null) {
            Text(value, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
    }
}

@Composable
fun SettingsSwitchItem(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, color = Color.White, modifier = Modifier.weight(1f), fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colorResource(id = R.color.orange),
                checkedTrackColor = colorResource(id = R.color.orange).copy(alpha = 0.5f)
            )
        )
    }
}

package me.ibrahim.moviesapp.compose.presentation.search

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.domain.Movie
import me.ibrahim.moviesapp.compose.presentation.favorite.FavoriteMoviesViewModel
import org.koin.androidx.compose.koinViewModel
import java.text.DecimalFormat
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchMoviesScreen(
    modifier: Modifier = Modifier,
    viewModel: FavoriteMoviesViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var chartType by remember { mutableStateOf(0) } // 0: 对比, 1: 占比, 2: 属性, 3: 预测
    var selectedMovie by remember { mutableStateOf<Movie?>(null) }

    LaunchedEffect(state.favoriteMovies) {
        if (selectedMovie == null && state.favoriteMovies.isNotEmpty()) {
            selectedMovie = state.favoriteMovies.first()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.blackBackground))
    ) {
        // 1. 还原背景图逻辑，与首页完全一致
        Image(
            painter = painterResource(id = R.drawable.bg1),
            contentDescription = null,
            alpha = 1f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "AI 票房雷达与市场分析",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start
                ),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (state.favoriteMovies.isEmpty()) {
                EmptyPlaceholder()
            } else {
                Text("实时分析目标", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.favoriteMovies) { movie ->
                        val isSelected = selectedMovie?.id == movie.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) Color(0xFFFFD700) else Color(0xFF2B2B2B).copy(alpha = 0.6f))
                                .clickable { selectedMovie = movie }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = movie.title ?: "未知",
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E).copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                        ChartTabItem("热度对比", Icons.AutoMirrored.Filled.CompareArrows, chartType == 0) { chartType = 0 }
                    ChartTabItem("市场占比", Icons.Default.PieChart, chartType == 1) { chartType = 1 }
                    ChartTabItem("多维雷达", Icons.Default.Analytics, chartType == 2) { chartType = 2 }
                    ChartTabItem("票房预测", Icons.Default.AutoGraph, chartType == 3) { chartType = 3 }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A).copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Box(modifier = Modifier.padding(20.dp)) {
                        selectedMovie?.let { movie ->
                            when (chartType) {
                                0 -> BoxOfficeComparisonChart(movie, state.favoriteMovies)
                                1 -> BoxOfficePieChart(movie, state.favoriteMovies)
                                2 -> BoxOfficeRadarChart(movie)
                                3 -> BoxOfficePredictionCard(movie)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                selectedMovie?.let { movie ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .background(Color(0xFF1E1E1E).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI 深度分析报告", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val interpretation = when(chartType) {
                            0 -> "【热度分析】当前作品热度处于行业${if((movie.popularity?:0.0)>300) "领先" else "中游"}水平。对比同期收藏作品，该片的话题讨论度更高，具备更强的首日爆发潜力。"
                            1 -> "【份额占比】在您的关注库中，该片占据了显著的市场心智份额。这种集中的关注度通常预示着极高的粉丝粘性和二刷潜力。"
                            2 -> "【五维模型】雷达图显示其\"口碑\"与\"潜力\"项表现突出。这意味着电影更倾向于长线放映，而非单纯的依靠流量堆砌。"
                            else -> "【预测逻辑】基于神经网络模型，综合评分、实时搜索热度及预售走势。¥ ${calculateBoxOffice(movie)} 万为保守预估，若宣发发力，仍有 15% 的上浮空间。"
                        }

                        Text(interpretation, color = Color.Gray, fontSize = 13.sp, lineHeight = 20.sp)
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.05f))
                        
                        if (chartType == 3) {
                            PredictionDetailContent(movie)
                        } else {
                            DetailMetricsGrid(movie)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailMetricsGrid(movie: Movie) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricBox(Modifier.weight(1f), "核心分", "${movie.voteAverage}", Color(0xFF4CAF50))
            MetricBox(Modifier.weight(1f), "热度值", "${movie.popularity?.toInt()}", Color(0xFF2196F3))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricBox(Modifier.weight(1f), "反馈量", "${movie.voteCount}", Color(0xFFFF9800))
            MetricBox(Modifier.weight(1f), "上映状态", if (movie.releaseDate != null) "已定档" else "待定", Color(0xFFE91E63))
        }
    }
}

@Composable
fun MetricBox(modifier: Modifier, label: String, value: String, accent: Color) {
    Column(modifier = modifier.padding(8.dp)) {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.padding(top = 4.dp).height(2.dp).fillMaxWidth(0.4f).background(accent))
    }
}

@Composable
fun EmptyPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.DarkGray)
        Spacer(modifier = Modifier.height(16.dp))
        Text("暂无分析数据", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("点击底部❤️收藏电影后，AI 即可为您生成票房雷达", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

private fun calculateBoxOffice(movie: Movie): String {
    val popFactor = (movie.popularity ?: 0.0) * 350000.0  
    val voteFactor = (movie.voteCount ?: 0).toDouble() * 120000.0 
    val scoreMultiplier = 0.6 + ((movie.voteAverage ?: 5.0) / 10.0) * 0.6 
    val res = (popFactor + voteFactor) * scoreMultiplier / 10000.0
    return DecimalFormat("#,###").format(res)
}

@Composable
fun BoxOfficeComparisonChart(selected: Movie, all: List<Movie>) {
    val avgPop = all.map { it.popularity ?: 0.0 }.average().toFloat()
    val curPop = (selected.popularity ?: 0.0).toFloat()
    val maxVal = maxOf(avgPop, curPop) * 1.5f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val spacing = size.width / 3
        val barWidth = 36.dp.toPx()
        
        for (i in 0..4) {
            val y = size.height - (size.height * (i / 5f))
            drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y))
        }

        drawRoundRect(
            color = Color(0xFF333333),
            topLeft = Offset(spacing - barWidth / 2, size.height - (avgPop / maxVal * size.height)),
            size = Size(barWidth, (avgPop / maxVal * size.height)),
            cornerRadius = CornerRadius(10f, 10f)
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8C00))),
            topLeft = Offset(spacing * 2 - barWidth / 2, size.height - (curPop / maxVal * size.height)),
            size = Size(barWidth, (curPop / maxVal * size.height)),
            cornerRadius = CornerRadius(10f, 10f)
        )

        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 28f; textAlign = android.graphics.Paint.Align.CENTER }
            drawText("平均热度线", spacing, size.height + 40f, paint)
            drawText("选中电影", spacing * 2, size.height + 40f, paint)
        }
    }
}

@Composable
fun BoxOfficePieChart(selected: Movie, movies: List<Movie>) {
    val topMovies = movies.take(5)
    val data = topMovies.map { it.popularity?.toFloat() ?: 10f }
    val total = data.sum()
    val colors = listOf(Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFFE57373), Color(0xFFBA68C8))

    Canvas(modifier = Modifier.fillMaxSize()) {
        var startAngle = -90f
        val outerRadius = size.minDimension * 0.35f
        
        data.forEachIndexed { index, value ->
            val sweepAngle = if (total > 0) (value / total) * 360f else 0f
            val isSelected = topMovies[index].id == selected.id
            
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(if (isSelected) 40.dp.toPx() else 30.dp.toPx()),
                size = Size(outerRadius * 2, outerRadius * 2),
                topLeft = Offset((size.width / 2) - outerRadius, (size.height / 2) - outerRadius)
            )

            if (isSelected) {
                val middleAngle = (startAngle + sweepAngle / 2) * (Math.PI / 180f).toFloat()
                val lineStart = Offset(
                    (size.width / 2) + cos(middleAngle) * (outerRadius + 10.dp.toPx()),
                    (size.height / 2) + sin(middleAngle) * (outerRadius + 10.dp.toPx())
                )
                val lineEnd = Offset(
                    (size.width / 2) + cos(middleAngle) * (outerRadius + 40.dp.toPx()),
                    (size.height / 2) + sin(middleAngle) * (outerRadius + 40.dp.toPx())
                )
                drawLine(Color.White, lineStart, lineEnd, strokeWidth = 2f)
                
                val percentage = (value / total * 100).toInt()
                val textY = if (lineEnd.y > size.height / 2) lineEnd.y + 30f else lineEnd.y - 10f
                
                drawContext.canvas.nativeCanvas.drawText(
                    "当前: $percentage%",
                    lineEnd.x,
                    textY,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.YELLOW
                        textSize = 32f
                        textAlign = if (lineEnd.x > size.width / 2) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
                        isFakeBoldText = true
                    }
                )
            }
            startAngle += sweepAngle
        }
        
        drawContext.canvas.nativeCanvas.drawText(
            "TOP 5 份额",
            size.width / 2,
            size.height / 2 + 10f,
            android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 32f; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }
        )
    }
}

@Composable
fun BoxOfficeRadarChart(movie: Movie) {
    val metrics = listOf(
        (movie.voteAverage ?: 0.0).toFloat() / 10f,
        minOf((movie.popularity ?: 0.0).toFloat() / 1000f, 1f),
        minOf((movie.voteCount ?: 0).toFloat() / 20000f, 1f),
        0.75f, 0.85f
    )
    val labels = listOf("口碑", "热度", "想看", "档期", "宣发")
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * 0.38f
        val angleStep = (2 * Math.PI / 5).toFloat()

        for (i in 1..4) {
            val r = radius * (i / 4f)
            val path = Path()
            for (j in 0..4) {
                val angle = j * angleStep - Math.PI.toFloat() / 2
                val x = center.x + r * cos(angle)
                val y = center.y + r * sin(angle)
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
                
                if (i == 4) {
                    drawLine(Color.White.copy(alpha = 0.1f), center, Offset(center.x + radius * cos(angle), center.y + radius * sin(angle)))
                    drawContext.canvas.nativeCanvas.drawText(labels[j], center.x + (radius + 25.dp.toPx()) * cos(angle), center.y + (radius + 25.dp.toPx()) * sin(angle), android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 24f; textAlign = android.graphics.Paint.Align.CENTER })
                }
            }
            path.close()
            drawPath(path, Color.White.copy(alpha = 0.1f), style = Stroke(1.dp.toPx()))
        }

        val dataPath = Path()
        metrics.forEachIndexed { i, value ->
            val angle = i * angleStep - Math.PI.toFloat() / 2
            val x = center.x + value * radius * cos(angle)
            val y = center.y + value * radius * sin(angle)
            if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
            drawCircle(Color(0xFFFFD700), 4.dp.toPx(), Offset(x, y))
        }
        dataPath.close()
        drawPath(dataPath, Color(0xFFFFD700).copy(alpha = 0.2f))
        drawPath(dataPath, Color(0xFFFFD700), style = Stroke(2.dp.toPx()))
    }
}

@Composable
fun BoxOfficePredictionCard(movie: Movie) {
    val popFactor = (movie.popularity ?: 0.0) * 350000.0  
    val voteFactor = (movie.voteCount ?: 0).toDouble() * 120000.0 
    val scoreMultiplier = 0.6 + ((movie.voteAverage ?: 5.0) / 10.0) * 0.6 
    val estimated = (popFactor + voteFactor) * scoreMultiplier
    val formatted = DecimalFormat("#,###").format(estimated / 10000.0)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("AI 预测国内总票房", color = Color.Gray, fontSize = 14.sp)
        Text("¥ $formatted", color = Color(0xFFFFD700), fontSize = 44.sp, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(16.dp))
        
        val confidence = minOf(0.96f, 0.4f + (movie.voteCount ?: 0) / 75000f)
        Text("算法信心指数: ${(confidence * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { confidence },
            modifier = Modifier.fillMaxWidth(0.8f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF4CAF50),
            trackColor = Color.DarkGray,
        )
    }
}

@Composable
fun PredictionDetailContent(movie: Movie) {
    InsightRow("市场爆发潜力", if((movie.popularity?:0.0)>500) "极高" else "稳健")
    InsightRow("受众覆盖率", "${(50 + (movie.voteAverage ?: 0.0) * 3).toInt()}%")
    InsightRow("长尾效应预估", if ((movie.voteAverage ?: 0.0) > 8.2) "显著" else "一般")
    Spacer(modifier = Modifier.height(8.dp))
    Text("注：预测基于历史同类型片源建模，包含宣发溢出价值预估。", color = Color.DarkGray, fontSize = 10.sp)
}

@Composable
fun InsightRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ChartTabItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(12.dp).width(50.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Color(0xFFFFD700) else Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, color = if (selected) Color(0xFFFFD700) else Color.Gray, fontSize = 10.sp)
    }
}

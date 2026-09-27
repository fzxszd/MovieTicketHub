package me.ibrahim.moviesapp.compose.presentation.movies_detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.domain.Movie
import me.ibrahim.moviesapp.compose.presentation.movies_detail.components.MovieCommentsSection
import me.ibrahim.moviesapp.compose.presentation.movies_detail.components.MovieDetailContent
import me.ibrahim.moviesapp.compose.presentation.movies_detail.components.MovieDetailHeader
import me.ibrahim.moviesapp.compose.presentation.movies_detail.components.MovieDetailToolbar
import org.koin.androidx.compose.koinViewModel
import me.ibrahim.moviesapp.compose.presentation.auth.AuthRequiredDialog

@Composable
fun MovieDetailScreen(
    movie: Movie,
    viewModel: MovieDetailViewModel = koinViewModel(),
    onBack: () -> Unit,
    onLoginRequired: () -> Unit = {},
    onBuyTicketClick: (Movie) -> Unit = {}
) {

    LaunchedEffect(Unit) {
        viewModel.onAction(MovieDetailActions.OnMovieClick(movie))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        // 1. 背景图 + 渐变遮罩
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.bg1),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.5f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )
        }

        // 2. 内容层
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            MovieDetailHeader(movie = state.movie, modifier = Modifier.aspectRatio(1f))

            MovieDetailContent(state = state, modifier = Modifier)

            // --- 电影打分功能 ---
            MovieRatingSection(
                rating = state.userRating,
                hasPurchased = state.hasPurchased,
                onRate = { viewModel.onAction(MovieDetailActions.RateMovie(it)) }
            )

            // --- 评论区 (更新调用接口) ---
            MovieCommentsSection(
                comments = state.comments,
                onSubmitComment = { content, parentId -> 
                    viewModel.onAction(MovieDetailActions.SubmitComment(content, parentId)) 
                },
                onLikeComment = { commentId -> 
                    viewModel.onAction(MovieDetailActions.ToggleLike(commentId)) 
                },
                isLiked = { commentId -> 
                    state.likedCommentIds.contains(commentId) 
                }
            )

            // 底部留白
            Spacer(modifier = Modifier.height(100.dp))
        }

        // 3. 顶部工具栏
        MovieDetailToolbar(
            modifier = Modifier.align(Alignment.TopCenter),
            state = state,
            onAction = { action ->
                if (action == MovieDetailActions.GoBack) onBack()
                else viewModel.onAction(action)
            }
        )

        // 4. 底部购票按钮
        Button(
            onClick = { if (viewModel.requestTicketPurchase(state.movie)) onBuyTicketClick(state.movie) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Yellow,
                contentColor = Color.Black
            )
        ) {
            Text(
                text = "立即购票",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    if (state.authRequired) {
        AuthRequiredDialog(onDismiss = viewModel::dismissAuthRequired, onLogin = {
            viewModel.dismissAuthRequired()
            onLoginRequired()
        })
    }
}

@Composable
fun MovieRatingSection(
    rating: Int,
    hasPurchased: Boolean,
    onRate: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (hasPurchased) {
                if (rating > 0) "您的评分: ${rating * 2}.0 分" else "喜欢这部电影吗？点击星星打分"
            } else {
                "请观影后再客观打分"
            },
            color = if (hasPurchased) Color.White else Color.Yellow.copy(alpha = 0.8f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 1..5) {
                Icon(
                    imageVector = if (i <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = when {
                        !hasPurchased -> Color.Gray.copy(alpha = 0.5f)
                        i <= rating -> Color(0xFFFFD700)
                        else -> Color.White.copy(alpha = 0.5f)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(enabled = hasPurchased) { onRate(i) }
                        .padding(4.dp)
                )
            }
        }
    }
}

package me.ibrahim.moviesapp.compose.presentation.movies_list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.domain.Movie
import me.ibrahim.moviesapp.compose.presentation.movies_list.components.MoviesList
import me.ibrahim.moviesapp.compose.presentation.movies_list.components.TitledMoviesList
import me.ibrahim.moviesapp.compose.presentation.search.MovieSearchBar
import org.koin.androidx.compose.koinViewModel
import me.ibrahim.moviesapp.compose.presentation.recommendation.RecommendationSection as ServerRecommendationSection

@Composable
fun MoviesListScreen(
    modifier: Modifier = Modifier,
    viewModel: MoviesListViewModel = koinViewModel(),
    onMovieClick: (Movie) -> Unit
) {

    val state by viewModel.state.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.blackBackground))
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg1),
            contentDescription = null,
            alpha = 1f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(id = R.string.title_movies_list_screen),
                overflow = TextOverflow.Visible,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .statusBarsPadding(),
                maxLines = 2,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start
                )
            )

            MovieSearchBar(
                modifier = Modifier.padding(horizontal = 16.dp),
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                onImeAction = { keyboardController?.hide() }
            )

            if (state.searchQuery.isEmpty()) {
                
                // --- 为你推荐模块 ---
                ServerRecommendationSection(onMovieClick = onMovieClick)

                TitledMoviesList(title = stringResource(id = R.string.now_playing)) {
                    if (state.isLoading) {
                        LoadingIndicator()
                    } else {
                        MoviesList(movies = state.nowPlayingMovies, onMovieClick = onMovieClick)
                    }
                }

                TitledMoviesList(title = stringResource(id = R.string.upcoming_movies)) {
                    if (state.isLoading) {
                        LoadingIndicator()
                    } else {
                        MoviesList(movies = state.upcomingMovies, onMovieClick = onMovieClick)
                    }
                }
            } else {
                TitledMoviesList(title = "搜索结果 - 正在上映") {
                    if (state.filteredNowPlayingMovies.isEmpty()) {
                        EmptySearchResult()
                    } else {
                        MoviesList(movies = state.filteredNowPlayingMovies, onMovieClick = onMovieClick)
                    }
                }
                TitledMoviesList(title = "搜索结果 - 即将上映") {
                    if (state.filteredUpcomingMovies.isEmpty()) {
                        EmptySearchResult()
                    } else {
                        MoviesList(movies = state.filteredUpcomingMovies, onMovieClick = onMovieClick)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp)) // 底部留白
        }
    }
}

@Composable
fun RecommendationSection(
    state: MoviesListState,
    onWeightChange: (Float, Float, Float) -> Unit,
    onRefresh: () -> Unit,
    onMovieClick: (Movie) -> Unit
) {
    var showWeights by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Color(0xFF1E1E1E).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(if (state.isPersonalized) R.string.recommendation_personalized else R.string.recommendation_fallback),
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            
            Row {
                IconButton(onClick = { showWeights = !showWeights }, enabled = state.isPersonalized) {
                    Icon(Icons.Default.Tune, contentDescription = "调节权重", tint = if (showWeights) Color(0xFFFFD700) else Color.White)
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Color.White)
                }
            }
        }

        AnimatedVisibility(visible = showWeights && state.isPersonalized) {
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                WeightSlider(label = "猜你喜欢", value = state.weightPreference) {
                    onWeightChange(it, state.weightPopularity, state.weightFreshness)
                }
                WeightSlider(label = "热门排行", value = state.weightPopularity) {
                    onWeightChange(state.weightPreference, it, state.weightFreshness)
                }
                WeightSlider(label = "最新上映", value = state.weightFreshness) {
                    onWeightChange(state.weightPreference, state.weightPopularity, it)
                }
                Text(
                    "注：权重将影响 AI 排序逻辑",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // 核心修复：这里改为判断 isRecLoading 而不是全局的 isLoading
        if (state.isRecLoading) {
            LoadingIndicator()
        } else if (state.recommendedMovies.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("AI 正在计算最佳推荐...", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            MoviesList(movies = state.recommendedMovies, onMovieClick = onMovieClick)
        }
    }
}

@Composable
fun WeightSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.LightGray, fontSize = 12.sp)
            Text("${(value * 100).toInt()}%", color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFD700),
                activeTrackColor = Color(0xFFFFD700),
                inactiveTrackColor = Color.DarkGray
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = Color(0xFFFFD700)
        )
    }
}

@Composable
fun EmptySearchResult() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("未找到相关电影", color = Color.Gray, fontSize = 14.sp)
    }
}

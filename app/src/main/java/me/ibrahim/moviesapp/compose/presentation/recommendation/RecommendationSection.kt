package me.ibrahim.moviesapp.compose.presentation.recommendation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.domain.Movie
import me.ibrahim.moviesapp.compose.domain.recommendation.*
import org.koin.androidx.compose.koinViewModel

/** A horizontal poster rail, consistent with the Now Playing rail. */
@Composable
fun RecommendationSection(onMovieClick:(Movie)->Unit, viewModel:RecommendationViewModel=koinViewModel(), modifier:Modifier=Modifier) {
    val state by viewModel.state.collectAsState()
    when (val current=state) {
        RecommendationState.Loading -> LinearProgressIndicator(modifier.fillMaxWidth().padding(horizontal=16.dp), color=colorResource(R.color.orange), trackColor=Color.White.copy(alpha=.18f))
        is RecommendationState.Error -> Row(modifier.padding(horizontal=16.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) { Text(stringResource(R.string.recommendation_unavailable),color=Color.White); Button(onClick=viewModel::refresh){Text(stringResource(R.string.retry))} }
        is RecommendationState.Content -> Column(modifier.fillMaxWidth()) {
            Text(current.value.sourceLabel,color=colorResource(R.color.orange),fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=16.dp))
            if(current.value.fallbackReason!=null) Text(stringResource(R.string.recommendation_general),color=Color.White.copy(alpha=.72f),style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(horizontal=16.dp,vertical=4.dp))
            LazyRow(contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                items(current.value.items,key={it.movieId}) { item ->
                    LaunchedEffect(current.value.recommendationId,item.movieId){viewModel.onMovieShown(current.value.recommendationId,item.movieId)}
                    RecommendationCard(item) { viewModel.onMovieClicked(current.value.recommendationId,item.movieId); onMovieClick(Movie(id=item.movieId,title=item.title,posterPath=item.posterUrl)) }
                }
            }
        }
    }
}

@Composable private fun RecommendationCard(item:RecommendationItem,onClick:()->Unit) {
    Card(shape=RoundedCornerShape(10.dp),colors=CardDefaults.cardColors(containerColor=colorResource(R.color.black4),contentColor=Color.White),modifier=Modifier.width(172.dp).clickable(onClick=onClick)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f/3f).background(Color(0xFF2A2A2C)),contentAlignment=Alignment.Center) {
                Icon(Icons.Default.Movie,null,tint=Color.White.copy(alpha=.35f),modifier=Modifier.size(42.dp))
                AsyncImage(model=item.posterUrl,contentDescription=item.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize().clip(RoundedCornerShape(topStart=10.dp,topEnd=10.dp)))
            }
            Text(item.title,color=Color.White,fontSize=17.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=10.dp,end=10.dp,top=9.dp))
            Text(item.reasonText,color=Color.White.copy(alpha=.72f),style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=10.dp,end=10.dp,top=4.dp,bottom=10.dp))
        }
    }
}

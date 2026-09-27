package me.ibrahim.moviesapp.compose.domain.recommendation

import kotlinx.coroutines.flow.StateFlow
interface RecommendationRepository {
    val state: StateFlow<RecommendationState>
    suspend fun refresh(limit:Int = 10)
    suspend fun reportEvent(recommendationId:String,movieId:Int,action:String)
    fun clearAccount()
}
sealed interface RecommendationState { data object Loading:RecommendationState; data class Content(val value:Recommendation):RecommendationState; data class Error(val retryable:Boolean=true):RecommendationState }

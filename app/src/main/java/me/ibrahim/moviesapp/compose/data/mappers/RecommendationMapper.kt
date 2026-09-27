package me.ibrahim.moviesapp.compose.data.mappers
import me.ibrahim.moviesapp.compose.data.dto.*
import me.ibrahim.moviesapp.compose.domain.recommendation.*
fun RecommendationResponseDto.toDomain():Recommendation {
    val source = RecommendationSource.valueOf(source)
    val state = PersonalizationState.valueOf(personalizationState)
    return Recommendation(recommendationId,source,sourceLabel,state,algorithmVersion,catalogVersion,favoriteRevision,fallbackReason,items.sortedBy { it.rank }.map { RecommendationItem(it.movieId,it.title,it.posterUrl,it.rank,RecommendationReasonType.valueOf(it.reasonType),it.reasonText) },retryable)
}

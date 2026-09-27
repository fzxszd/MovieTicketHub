package me.ibrahim.moviesapp.compose.domain.recommendation

enum class RecommendationSource { PERSONALIZED, FALLBACK_POPULAR, FALLBACK_NOW_PLAYING }
enum class PersonalizationState { CURRENT, PREFERENCES_SYNCING, NOT_APPLICABLE }
enum class RecommendationReasonType { SHARED_GENRE, SHARED_THEME, COLLABORATIVE, POPULAR, NOW_PLAYING }
data class RecommendationItem(val movieId:Int,val title:String,val posterUrl:String?,val rank:Int,val reasonType:RecommendationReasonType,val reasonText:String)
data class Recommendation(val recommendationId:String,val source:RecommendationSource,val sourceLabel:String,val personalizationState:PersonalizationState,val algorithmVersion:String,val catalogVersion:String,val favoriteRevision:Long?,val fallbackReason:String?,val items:List<RecommendationItem>,val retryable:Boolean)

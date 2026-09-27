package me.ibrahim.moviesapp.compose.data.dto
import kotlinx.serialization.Serializable
@Serializable data class RecommendationItemDto(val movieId:Int,val title:String,val posterUrl:String?=null,val availability:String="AVAILABLE",val rank:Int,val reasonType:String,val reasonText:String)
@Serializable data class RecommendationResponseDto(val recommendationId:String,val source:String,val sourceLabel:String,val personalizationState:String,val algorithmVersion:String,val catalogVersion:String,val favoriteRevision:Long?=null,val fallbackReason:String?=null,val items:List<RecommendationItemDto> = emptyList(),val retryable:Boolean=true)
@Serializable data class RecommendationEventDto(val eventId:String,val movieId:Int,val action:String)

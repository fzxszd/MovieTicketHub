package me.ibrahim.moviesapp.compose.data.dto

import kotlinx.serialization.Serializable

@Serializable data class FavoriteSnapshotDto(val title:String?=null,val posterUrl:String?=null,val availability:String="AVAILABLE")
@Serializable data class FavoriteChangeDto(val movieId:Int,val isFavorite:Boolean,val serverRevision:Long,val acceptedAt:String,val movie:FavoriteSnapshotDto?=null)
@Serializable data class FavoriteOperationDto(val clientOperationId:String,val movieId:Int,val targetState:Boolean,val localSequence:Long)
@Serializable data class FavoriteOperationResultDto(val clientOperationId:String,val movieId:Int,val result:String,val authoritativeState:Boolean,val serverRevision:Long,val errorCode:String?=null)
@Serializable data class FavoriteChangesDto(val serverRevision:Long,val changes:List<FavoriteChangeDto>,val hasMore:Boolean,val nextRevision:Long,val operationResults:List<FavoriteOperationResultDto> = emptyList())
@Serializable data class FavoriteSetRequestDto(val targetState:Boolean,val deviceId:String,val localSequence:Long)

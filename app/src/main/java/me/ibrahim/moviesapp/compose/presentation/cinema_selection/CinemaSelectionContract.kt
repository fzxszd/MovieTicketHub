package me.ibrahim.moviesapp.compose.presentation.cinema_selection

import me.ibrahim.moviesapp.compose.domain.cinema.*

enum class CinemaLoadState { IDLE, LOADING, CONTENT, EMPTY, ERROR }
data class CinemaFilterDraft(
    val districts:Set<String> = emptySet(), val startTime:String?=null,val endTime:String?=null,
    val minPriceMinor:Long?=null,val maxPriceMinor:Long?=null)
data class CinemaSelectionState(
    val movieId:Int=0,val movieTitle:String="",val cityCode:String="CN-SH",val dates:List<String> = emptyList(),
    val selectedDate:String?=null,val loadState:CinemaLoadState=CinemaLoadState.IDLE,
    val cinemas:List<Cinema> = emptyList(),val availableDistricts:List<String> = emptyList(),
    val filterDraft:CinemaFilterDraft=CinemaFilterDraft(),val appliedFilter:CinemaFilterDraft=CinemaFilterDraft(),
    val sort:SortType=SortType.RECOMMENDED,val sortNotice:SortNotice?=null,val error:CinemaError?=null)
sealed interface CinemaSelectionEffect { data class OpenCinema(val movieId:Int,val cinemaId:String,val localDate:String):CinemaSelectionEffect }

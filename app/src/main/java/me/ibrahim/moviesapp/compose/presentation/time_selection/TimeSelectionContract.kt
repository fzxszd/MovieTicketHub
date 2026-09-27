package me.ibrahim.moviesapp.compose.presentation.time_selection

import me.ibrahim.moviesapp.compose.domain.cinema.*

enum class TimeLoadState{LOADING,CONTENT,EMPTY,ERROR}
data class TimeSelectionState(
    val movieId:Int=0,val cinemaId:String="",val localDate:String="",val cinema:Cinema?=null,
    val loadState:TimeLoadState=TimeLoadState.LOADING,val error:CinemaError?=null,
    val validatingId:String?=null,val authRequired:Boolean=false,val notice:String?=null)
sealed interface TimeSelectionEffect{data class Ready(val showtime:Showtime):TimeSelectionEffect}

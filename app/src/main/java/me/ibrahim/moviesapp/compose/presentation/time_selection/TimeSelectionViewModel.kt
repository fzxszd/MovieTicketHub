package me.ibrahim.moviesapp.compose.presentation.time_selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository
import me.ibrahim.moviesapp.compose.domain.auth.PendingAuthAction
import me.ibrahim.moviesapp.compose.domain.cinema.*

class TimeSelectionViewModel(private val repository:CinemaRepository,private val authRepository:AuthRepository):ViewModel(){
    private val _state=MutableStateFlow(TimeSelectionState());val state:StateFlow<TimeSelectionState> = _state.asStateFlow()
    private val channel=Channel<TimeSelectionEffect>(Channel.BUFFERED);val effects=channel.receiveAsFlow()
    fun start(movieId:Int,cinemaId:String,localDate:String){
        if(_state.value.movieId==movieId&&_state.value.cinemaId==cinemaId&&_state.value.localDate==localDate&&_state.value.cinema!=null)return
        _state.value=TimeSelectionState(movieId,cinemaId,localDate);load()
    }
    fun retry()=load()
    fun dismissAuth(){_state.update{it.copy(authRequired=false)}}
    fun select(showtime:Showtime){
        if(_state.value.validatingId!=null||!showtime.selectable)return
        viewModelScope.launch{_state.update{it.copy(validatingId=showtime.id,notice=null)}
            when(val result=repository.validate(showtime.id,showtime.version)){
                is CinemaResult.Failure->_state.update{it.copy(validatingId=null,error=result.error,notice="VALIDATION_FAILED")}
                is CinemaResult.Success->{val validation=result.value
                    when(validation.result){
                        ValidationResult.UNAVAILABLE->{replace(validation.latest);_state.update{it.copy(validatingId=null,notice="SHOWTIME_UNAVAILABLE")}}
                        ValidationResult.CHANGED->{replace(validation.latest);_state.update{it.copy(validatingId=null,notice="SHOWTIME_CHANGED")}}
                        ValidationResult.UNCHANGED->{
                            if(!authRepository.requireAuthentication(PendingAuthAction.SelectSeats(showtime.movieId,showtime.cinemaId,showtime.id)))
                                _state.update{it.copy(validatingId=null,authRequired=true)}
                            else{_state.update{it.copy(validatingId=null)};channel.send(TimeSelectionEffect.Ready(validation.latest))}
                        }
                    }
                }
            }}
    }
    private fun replace(latest: Showtime) = _state.update { state ->
        state.copy(cinema = state.cinema?.let { cinema ->
            cinema.copy(showtimes = cinema.showtimes.map { if (it.id == latest.id) latest else it })
        })
    }
    private fun load(){val s=_state.value;viewModelScope.launch{_state.update{it.copy(loadState=TimeLoadState.LOADING,error=null)}
        when(val result=repository.showtimes(ShowtimeQuery(s.movieId,s.localDate))){
            is CinemaResult.Success->{val cinema=result.value.cinemas.firstOrNull{it.id==s.cinemaId};_state.update{it.copy(cinema=cinema,loadState=if(cinema==null)TimeLoadState.EMPTY else TimeLoadState.CONTENT)}}
            is CinemaResult.Failure->_state.update{it.copy(loadState=TimeLoadState.ERROR,error=result.error)}
        }}}
}

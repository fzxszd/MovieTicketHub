package me.ibrahim.moviesapp.compose.presentation.cinema_selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.ibrahim.moviesapp.compose.domain.cinema.*

class CinemaSelectionViewModel(private val repository:CinemaRepository):ViewModel(){
    private val _state=MutableStateFlow(CinemaSelectionState());val state:StateFlow<CinemaSelectionState> = _state.asStateFlow()
    private val effectsChannel=Channel<CinemaSelectionEffect>(Channel.BUFFERED);val effects=effectsChannel.receiveAsFlow()
    fun start(movieId:Int,movieTitle:String,cityCode:String="CN-SH"){
        if(_state.value.movieId==movieId&&_state.value.dates.isNotEmpty())return
        _state.value=CinemaSelectionState(movieId=movieId,movieTitle=movieTitle,cityCode=cityCode,loadState=CinemaLoadState.LOADING)
        viewModelScope.launch { when(val result=repository.availableDates(movieId,cityCode)){
            is CinemaResult.Success->{val first=result.value.firstOrNull();_state.update{it.copy(dates=result.value,selectedDate=first)};if(first==null)_state.update{it.copy(loadState=CinemaLoadState.EMPTY)} else load()}
            is CinemaResult.Failure->_state.update{it.copy(loadState=CinemaLoadState.ERROR,error=result.error)} }}
    }
    fun selectDate(value:String){_state.update{it.copy(selectedDate=value)};load()}
    fun retry()=if(_state.value.dates.isEmpty())start(_state.value.movieId,_state.value.movieTitle,_state.value.cityCode) else load()
    fun updateDraft(value:CinemaFilterDraft)=_state.update{it.copy(filterDraft=value)}
    fun applyFilters(){_state.update{it.copy(appliedFilter=it.filterDraft)};load()}
    fun clearFilters(){_state.update{it.copy(filterDraft=CinemaFilterDraft(),appliedFilter=CinemaFilterDraft())};load()}
    fun changeSort(sort:SortType){_state.update{it.copy(sort=sort)};load()}
    fun selectCinema(cinema:Cinema){val date=_state.value.selectedDate?:return;viewModelScope.launch{effectsChannel.send(CinemaSelectionEffect.OpenCinema(_state.value.movieId,cinema.id,date))}}
    private fun load(){val s=_state.value;val date=s.selectedDate?:return;val f=s.appliedFilter
        viewModelScope.launch{_state.update{it.copy(loadState=CinemaLoadState.LOADING,error=null)}
            val query=ShowtimeQuery(s.movieId,date,s.cityCode,f.districts,f.startTime,f.endTime,f.minPriceMinor,f.maxPriceMinor,s.sort)
            when(val result=repository.showtimes(query)){
                is CinemaResult.Success->_state.update{it.copy(loadState=if(result.value.cinemas.isEmpty())CinemaLoadState.EMPTY else CinemaLoadState.CONTENT,
                    cinemas=result.value.cinemas,availableDistricts=result.value.availableDistricts,sortNotice=result.value.sortNotice,error=null)}
                is CinemaResult.Failure->_state.update{it.copy(loadState=CinemaLoadState.ERROR,error=result.error)}
            }
        }
    }
}

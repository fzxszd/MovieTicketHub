package me.ibrahim.moviesapp.compose.presentation.movies_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ibrahim.moviesapp.compose.domain.MoviesRepository
import me.ibrahim.moviesapp.compose.domain.onError
import me.ibrahim.moviesapp.compose.domain.onSuccess
import me.ibrahim.moviesapp.compose.presentation.main.toUiText
import java.util.concurrent.atomic.AtomicInteger

class MoviesListViewModel(private val repository:MoviesRepository):ViewModel(){
 private var nowJob:Job?=null;private var upcomingJob:Job?=null;private val active=AtomicInteger(0);private val _state=MutableStateFlow(MoviesListState());val state=_state.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),_state.value)
 init{fetchNowPlayingMovies();fetchUpcomingMovies()}
 fun onSearchQueryChange(q:String){_state.update{it.copy(searchQuery=q,filteredNowPlayingMovies=if(q.isBlank())it.nowPlayingMovies else it.nowPlayingMovies.filter{m->m.title?.contains(q,true)==true},filteredUpcomingMovies=if(q.isBlank())it.upcomingMovies else it.upcomingMovies.filter{m->m.title?.contains(q,true)==true})}}
 private fun fetchUpcomingMovies(){upcomingJob?.cancel();upcomingJob=viewModelScope.launch(Dispatchers.IO){begin();try{repository.fetchUpcomingMovies().onSuccess{movies->_state.update{it.copy(upcomingMovies=movies,filteredUpcomingMovies=if(it.searchQuery.isBlank())movies else movies.filter{m->m.title?.contains(it.searchQuery,true)==true},errorMsg=null)}}.onError{e->_state.update{it.copy(upcomingMovies=emptyList(),errorMsg=e.toUiText())}}}finally{end()}}}
 private fun fetchNowPlayingMovies(){nowJob?.cancel();nowJob=viewModelScope.launch(Dispatchers.IO){begin();try{repository.fetchNowPlayingMovies().onSuccess{movies->_state.update{it.copy(nowPlayingMovies=movies,filteredNowPlayingMovies=if(it.searchQuery.isBlank())movies else movies.filter{m->m.title?.contains(it.searchQuery,true)==true},errorMsg=null)}}.onError{e->_state.update{it.copy(nowPlayingMovies=emptyList(),errorMsg=e.toUiText())}}}finally{end()}}}
 private fun begin(){active.incrementAndGet();_state.update{it.copy(isLoading=true)}}
 private fun end(){if(active.decrementAndGet()<=0){active.set(0);_state.update{it.copy(isLoading=false)}}}
}

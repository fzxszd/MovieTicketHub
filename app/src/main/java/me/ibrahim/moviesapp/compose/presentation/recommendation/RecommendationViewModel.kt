package me.ibrahim.moviesapp.compose.presentation.recommendation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.ibrahim.moviesapp.compose.domain.recommendation.RecommendationRepository
import me.ibrahim.moviesapp.compose.domain.recommendation.RecommendationState
class RecommendationViewModel(private val repository:RecommendationRepository):ViewModel(){val state:StateFlow<RecommendationState> = repository.state;init{refresh()};fun refresh()=viewModelScope.launch{repository.refresh()};fun onMovieShown(id:String,movieId:Int)=viewModelScope.launch{repository.reportEvent(id,movieId,"IMPRESSION")};fun onMovieClicked(id:String,movieId:Int)=viewModelScope.launch{repository.reportEvent(id,movieId,"CLICK")};fun clearAccount()=repository.clearAccount()}

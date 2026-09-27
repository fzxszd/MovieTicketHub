package me.ibrahim.moviesapp.compose.presentation.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository
import me.ibrahim.moviesapp.compose.domain.auth.AuthState
import me.ibrahim.moviesapp.compose.domain.favorite.FavoriteRepository
import me.ibrahim.moviesapp.compose.domain.Movie

class FavoriteMoviesViewModel(
    private val authRepository: AuthRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FavoriteMoviesState())
    
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        _state.value
    )

    init {
        observeUserAndData()
    }

    private fun observeUserAndData() {
        viewModelScope.launch {
            authRepository.state.collectLatest { authState ->
                val user = (authState as? AuthState.Authenticated)?.user
                _state.value = FavoriteMoviesState(isLoading = user != null)
                if (user != null) favoriteRepository.observeFavorites(user.id).collect { favorites ->
                    _state.update { it.copy(isLoading = false, error = null, favoriteMovies = favorites.map { f -> Movie(id=f.movieId, title=f.title, posterPath=f.posterPath) }) }
                }
            }
        }
    }
}

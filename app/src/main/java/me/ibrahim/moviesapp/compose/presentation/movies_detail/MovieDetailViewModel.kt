package me.ibrahim.moviesapp.compose.presentation.movies_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.ibrahim.moviesapp.compose.domain.Movie
import me.ibrahim.moviesapp.compose.domain.MoviesRepository
import me.ibrahim.moviesapp.compose.domain.onError
import me.ibrahim.moviesapp.compose.domain.onSuccess
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository
import me.ibrahim.moviesapp.compose.domain.auth.AuthState
import me.ibrahim.moviesapp.compose.domain.auth.PendingAuthAction
import me.ibrahim.moviesapp.compose.presentation.main.toUiText
import me.ibrahim.moviesapp.compose.domain.favorite.FavoriteRepository
import me.ibrahim.moviesapp.compose.domain.favorite.FavoriteResult

class MovieDetailViewModel(
    private val moviesRepository: MoviesRepository,
    private val authRepository: AuthRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private var markFavoriteJob: Job? = null
    private var fetchActorsJob: Job? = null
    private var observeUserJob: Job? = null
    private var observeFavoriteStateJob: Job? = null
    private var observePurchaseJob: Job? = null
    private var observeRatingJob: Job? = null
    private var observeCommentsJob: Job? = null
    private var observeLikedCommentsJob: Job? = null
    private var userEmail: String? = null

    private val _state = MutableStateFlow(MovieDetailState())
    val state = _state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            _state.value
        )

    init {
        observeUserAndData()
    }

    private fun observeUserAndData() {
        observeUserJob?.cancel()
        observeUserJob = authRepository.state
            .map { (it as? AuthState.Authenticated)?.user }
            .onEach { user ->
                userEmail = user?.email
                if (user != null) {
                    observePurchaseStatus(user.email)
                    observePersistentData(user.email)
                } else {
                    observePurchaseJob?.cancel()
                    observeFavoriteStateJob?.cancel()
                    observeRatingJob?.cancel(); observeLikedCommentsJob?.cancel()
                    _state.update { it.copy(isFavorite = false, hasPurchased = false,
                        userRating = 0, likedCommentIds = emptySet()) }
                }
            }.launchIn(viewModelScope)
    }

    private fun observePurchaseStatus(email: String) {
        observePurchaseJob?.cancel()
        observePurchaseJob = moviesRepository
            .hasPurchasedMovie(_state.value.movie.id, email)
            .onEach { hasPurchased ->
                _state.update {
                    it.copy(hasPurchased = hasPurchased)
                }
            }.launchIn(viewModelScope)
    }

    private fun observePersistentData(email: String) {
        val movieId = _state.value.movie.id
        
        observeRatingJob?.cancel()
        observeRatingJob = moviesRepository.getRating(movieId, email)
            .onEach { rating ->
                _state.update { it.copy(userRating = rating ?: 0) }
            }.launchIn(viewModelScope)

        observeCommentsJob?.cancel()
        observeCommentsJob = moviesRepository.getComments(movieId)
            .onEach { comments ->
                _state.update { it.copy(comments = comments) }
            }.launchIn(viewModelScope)

        observeLikedCommentsJob?.cancel()
        observeLikedCommentsJob = moviesRepository.getLikedCommentIds(email)
            .onEach { likedIds ->
                _state.update { it.copy(likedCommentIds = likedIds) }
            }.launchIn(viewModelScope)
    }

    fun onAction(action: MovieDetailActions) {
        when (action) {
            MovieDetailActions.GoBack -> Unit
            is MovieDetailActions.MarkFavorite -> markFavorite(action.movie)
            is MovieDetailActions.OnMovieClick -> {
                _state.update {
                    it.copy(
                        movie = action.movie,
                        actors = emptyList(),
                        errorMessage = null
                    )
                }
                fetchMovieDescription(action.movie.id)
                fetchActorsList(action.movie.id)
                observeFavoriteStateJob?.cancel()
                authRepository.currentUser()?.let { user ->
                    observeFavoriteStateJob = favoriteRepository.observe(user.id, action.movie.id).onEach { favorite ->
                        _state.update { it.copy(isFavorite = favorite?.isFavorite == true, favoriteStatus = favorite?.status ?: me.ibrahim.moviesapp.compose.domain.favorite.FavoriteSyncStatus.CONFIRMED) }
                    }.launchIn(viewModelScope)
                }
                userEmail?.let { email ->
                    observePurchaseStatus(email)
                    observePersistentData(email)
                }
            }
            is MovieDetailActions.RateMovie -> {
                val email = userEmail ?: return
                viewModelScope.launch {
                    moviesRepository.saveRating(_state.value.movie.id, email, action.rating)
                }
            }
            is MovieDetailActions.SubmitComment -> {
                val email = userEmail ?: return
                viewModelScope.launch {
                    val user = authRepository.currentUser()
                    moviesRepository.saveComment(
                        movieId = _state.value.movie.id,
                        userEmail = email,
                        userName = user?.username ?: "User",
                        content = action.content,
                        parentId = action.parentId // 修复点：传递 parentId
                    )
                }
            }
            is MovieDetailActions.ToggleLike -> {
                val email = userEmail ?: return
                viewModelScope.launch {
                    moviesRepository.toggleCommentLike(action.commentId, email)
                }
            }
        }
    }

    private fun fetchMovieDescription(movieId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            moviesRepository.fetchMovieDetail(movieId).onSuccess { description ->
                _state.update {
                    if (it.movie.id == movieId) {
                        it.copy(movie = it.movie.copy(overview = description))
                    } else {
                        it
                    }
                }
            }
        }
    }


    private fun fetchActorsList(movieId: Int) {
        fetchActorsJob?.cancel()
        fetchActorsJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            moviesRepository.fetchMovieActors(movieId = movieId)
                .onSuccess { actors ->
                    _state.update {
                        if (it.movie.id == movieId) {
                            it.copy(actors = actors, isLoading = false)
                        } else {
                            it
                        }
                    }
                }
                .onError { error ->
                    _state.update {
                        if (it.movie.id == movieId) {
                            it.copy(isLoading = false, errorMessage = error.toUiText())
                        } else {
                            it
                        }
                    }
                }
        }
    }

    private fun markFavorite(movie: Movie) {
        if (!authRepository.requireAuthentication(PendingAuthAction.FavoriteMovie(movie.id))) {
            _state.update { it.copy(authRequired = true) }
            return
        }
        val accountId = authRepository.currentUser()?.id ?: return
        markFavoriteJob?.cancel()

        markFavoriteJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(favoriteStatus = me.ibrahim.moviesapp.compose.domain.favorite.FavoriteSyncStatus.SYNCING) }
            when (val result = favoriteRepository.setFavorite(accountId, movie.id, !state.value.isFavorite, me.ibrahim.moviesapp.compose.domain.favorite.Favorite(movie.id, !state.value.isFavorite, state.value.isFavorite, me.ibrahim.moviesapp.compose.domain.favorite.FavoriteSyncStatus.PENDING, movie.title, movie.posterPath))) {
                is FavoriteResult.Success -> _state.update { it.copy(isFavorite = result.value.isFavorite, favoriteStatus = result.value.status) }
                is FavoriteResult.Failure -> _state.update { it.copy(favoriteStatus = me.ibrahim.moviesapp.compose.domain.favorite.FavoriteSyncStatus.FAILED) }
            }
        }
    }

    fun dismissAuthRequired() { _state.update { it.copy(authRequired = false) } }

    fun requestTicketPurchase(movie: Movie): Boolean {
        val allowed = authRepository.requireAuthentication(PendingAuthAction.SelectSeats(movie.id, "", ""))
        if (!allowed) _state.update { it.copy(authRequired = true) }
        return allowed
    }
}

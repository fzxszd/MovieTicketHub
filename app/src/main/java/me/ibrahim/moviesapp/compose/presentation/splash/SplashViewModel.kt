package me.ibrahim.moviesapp.compose.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository
import me.ibrahim.moviesapp.compose.domain.auth.AuthState

sealed interface SplashRoute {
    data object Loading : SplashRoute
    data object Main : SplashRoute
    data object Retry : SplashRoute
}

class SplashViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _route = MutableStateFlow<SplashRoute>(SplashRoute.Loading)
    val route: StateFlow<SplashRoute> = _route.asStateFlow()

    fun restore() {
        if (_route.value != SplashRoute.Loading && _route.value != SplashRoute.Retry) return
        _route.value = SplashRoute.Loading
        viewModelScope.launch {
            _route.value = when (authRepository.restoreSession()) {
                is AuthState.RecoverableError -> SplashRoute.Retry
                else -> SplashRoute.Main
            }
        }
    }

    fun continueAsGuest() { _route.value = SplashRoute.Main }
}

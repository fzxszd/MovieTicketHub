package me.ibrahim.moviesapp.compose.core

import android.app.Application
import me.ibrahim.moviesapp.compose.di.coreModule
import me.ibrahim.moviesapp.compose.di.networkModule
import me.ibrahim.moviesapp.compose.di.repositoryModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.ibrahim.moviesapp.compose.domain.MoviesRepository
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository
import me.ibrahim.moviesapp.compose.domain.auth.AuthState
import me.ibrahim.moviesapp.compose.domain.favorite.FavoriteRepository

class MoviesApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val koinApplication = startKoin {
            androidContext(this@MoviesApp)
            androidLogger(level = Level.ERROR)
            modules(repositoryModule, networkModule, coreModule)
        }
        val authRepository = koinApplication.koin.get<AuthRepository>()
        val moviesRepository = koinApplication.koin.get<MoviesRepository>()
        val favoriteRepository = koinApplication.koin.get<FavoriteRepository>()
        var activeFavoriteAccount: String? = null
        applicationScope.launch {
            authRepository.state.collectLatest { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        if (activeFavoriteAccount != null && activeFavoriteAccount != state.user.id) favoriteRepository.onAccountStopped(activeFavoriteAccount!!, true)
                        activeFavoriteAccount = state.user.id
                        moviesRepository.startAutoSync(state.user.email); favoriteRepository.onAccountStarted(state.user.id)
                    }
                    AuthState.Guest -> { activeFavoriteAccount?.let { favoriteRepository.onAccountStopped(it, true) }; activeFavoriteAccount = null; moviesRepository.stopAutoSync() }
                    else -> Unit
                }
            }
        }
    }
}

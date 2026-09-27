package me.ibrahim.moviesapp.compose.presentation.splash

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.ibrahim.moviesapp.compose.core.BaseActivity
import me.ibrahim.moviesapp.compose.presentation.main.MainActivity
import org.koin.androidx.compose.koinViewModel

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {
    @Composable
    override fun InitView() {
        val viewModel: SplashViewModel = koinViewModel()
        val route by viewModel.route.collectAsState()
        LaunchedEffect(Unit) { viewModel.restore() }
        LaunchedEffect(route) {
            if (route == SplashRoute.Main) {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            }
        }
        SplashScreen(
            isLoading = route == SplashRoute.Loading,
            showRetry = route == SplashRoute.Retry,
            onButtonClick = viewModel::restore,
            onContinueAsGuest = viewModel::continueAsGuest
        )
    }
}

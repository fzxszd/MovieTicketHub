package me.ibrahim.moviesapp.compose.core

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import me.ibrahim.moviesapp.compose.ui.theme.MoviesAppCompTheme

abstract class BaseActivity : ComponentActivity() {

    @Composable
    abstract fun InitView()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MoviesAppCompTheme { InitView() } }
    }
}
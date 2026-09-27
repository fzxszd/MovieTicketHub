package me.ibrahim.moviesapp.compose.presentation.main

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import me.ibrahim.moviesapp.compose.core.BaseActivity
import me.ibrahim.moviesapp.compose.core.MoviesNavGraph
import me.ibrahim.moviesapp.compose.presentation.main.components.BottomNavigationBar

class MainActivity : BaseActivity() {

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @Composable
    override fun InitView() {
        val navController = rememberNavController()
        Scaffold(
            bottomBar = {
                BottomNavigationBar(navController = navController)
            }
        ) {
            MoviesNavGraph(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = it.calculateBottomPadding()),
                navController = navController
            )
        }
    }
}



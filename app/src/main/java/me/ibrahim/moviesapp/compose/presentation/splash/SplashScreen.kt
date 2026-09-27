package me.ibrahim.moviesapp.compose.presentation.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.presentation.splash.components.FooterSection
import me.ibrahim.moviesapp.compose.presentation.splash.components.HeaderSection

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SplashScreenPrev() {
    SplashScreen(modifier = Modifier, onButtonClick = {})
}

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    showRetry: Boolean = false,
    onButtonClick: () -> Unit,
    onContinueAsGuest: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.blackBackground))
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween

        ) {
            HeaderSection()
            when {
                isLoading -> CircularProgressIndicator()
                showRetry -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FooterSection(onButtonClick = onButtonClick)
                    TextButton(onClick = onContinueAsGuest) {
                        Text(stringResource(R.string.continue_as_guest), color = Color.White)
                    }
                }
                else -> FooterSection(onButtonClick = onButtonClick)
            }
        }
    }
}





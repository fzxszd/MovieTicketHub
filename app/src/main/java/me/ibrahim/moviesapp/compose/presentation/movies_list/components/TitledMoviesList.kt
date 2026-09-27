package me.ibrahim.moviesapp.compose.presentation.movies_list.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ibrahim.moviesapp.compose.R

@Composable
fun TitledMoviesList(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            modifier = modifier.padding(horizontal = 16.dp),
            maxLines = 1,
            style = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = colorResource(id = R.color.orange),
                textAlign = TextAlign.Start,
            )
        )
        content()
    }
}
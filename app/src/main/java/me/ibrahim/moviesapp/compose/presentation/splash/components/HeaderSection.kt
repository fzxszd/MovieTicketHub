package me.ibrahim.moviesapp.compose.presentation.splash.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import me.ibrahim.moviesapp.compose.R

@Composable
fun HeaderSection() {
    ConstraintLayout {
        val (bg1, women, title, description) = createRefs()

        val topGuideline = createGuidelineFromTop(0.1f)

        Image(
            painter = painterResource(id = R.drawable.bg1), contentDescription = null, modifier = Modifier.constrainAs(bg1) {
                top.linkTo(parent.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }, contentScale = ContentScale.Crop
        )

        Image(painter = painterResource(id = R.drawable.woman), contentDescription = null, modifier = Modifier
            .constrainAs(women) {
                top.linkTo(topGuideline)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
            .padding(20.dp))

        Text(text = "Watch Videos in\nVirtual Reality", style = TextStyle(
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 40.sp, textAlign = TextAlign.Center
        ), modifier = Modifier.constrainAs(title) {
            top.linkTo(women.bottom)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
        })

        Text(text = "Download and watch offline\nwhereever you are!", style = TextStyle(
            color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center
        ), modifier = Modifier.constrainAs(description) {
            top.linkTo(title.bottom, 16.dp)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
        })
    }
}
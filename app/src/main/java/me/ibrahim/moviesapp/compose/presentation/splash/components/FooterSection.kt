package me.ibrahim.moviesapp.compose.presentation.splash.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import me.ibrahim.moviesapp.compose.R

@Composable
fun FooterSection(onButtonClick: () -> Unit) {
    ConstraintLayout {
        val (bg2, button) = createRefs()
        Image(painter = painterResource(id = R.drawable.bg2), contentDescription = null, modifier = Modifier
            .height(200.dp)
            .constrainAs(bg2) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            })

        Button(
            modifier = Modifier
                .size(200.dp, 50.dp)
                .constrainAs(button) {
                    top.linkTo(bg2.top)
                    bottom.linkTo(bg2.bottom)
                    start.linkTo(bg2.start)
                    end.linkTo(bg2.end)
                }, shape = RoundedCornerShape(50.dp), colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent, contentColor = Color.White
            ), border = BorderStroke(
                width = 4.dp, brush = Brush.linearGradient(
                    listOf(
                        colorResource(id = R.color.pink), colorResource(id = R.color.green)
                    )
                )
            ), onClick = onButtonClick
        ) {
            Text(
                text = "Get In", fontSize = 18.sp, color = Color.White
            )
        }
    }
}
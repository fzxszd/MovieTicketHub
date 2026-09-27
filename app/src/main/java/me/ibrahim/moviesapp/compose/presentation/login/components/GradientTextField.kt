package me.ibrahim.moviesapp.compose.presentation.login.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ibrahim.moviesapp.compose.R

@Composable
fun GradientTextField(
    modifier: Modifier = Modifier,
    value: String,
    hint: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onValueChange: (String) -> Unit
) {
    Box(
        modifier = modifier
            .height(60.dp)
            .border(
                width = 4.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        colorResource(id = R.color.pink),
                        colorResource(id = R.color.green)
                    )
                ),
                shape = RoundedCornerShape(50.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        OutlinedTextField(
            value = value,
            singleLine = true,
            visualTransformation = visualTransformation,
            textStyle = TextStyle(
                color = Color.White, fontSize = 20.sp,
                textAlign = TextAlign.Center
            ),
            placeholder = {
                Text(
                    text = hint,
                    color = Color.White.copy(alpha = 0.5f),
                    style = TextStyle(
                        color = Color.White, fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = Color.White,
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White
            ),
            modifier = modifier
                .background(
                    color = colorResource(id = R.color.black1),
                    shape = RoundedCornerShape(50.dp)
                ),
            onValueChange = onValueChange,
            keyboardOptions = keyboardOptions
        )
    }
}
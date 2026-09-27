package me.ibrahim.moviesapp.compose.presentation.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ibrahim.moviesapp.compose.R
import me.ibrahim.moviesapp.compose.presentation.login.components.GradientButton
import me.ibrahim.moviesapp.compose.presentation.login.components.GradientTextField
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(viewModel: LoginViewModel = koinViewModel(), onLoginSuccess: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it == LoginEvent.Authenticated) onLoginSuccess() }
    }
    Box(Modifier.fillMaxSize().background(colorResource(R.color.blackBackground))) {
        Image(painterResource(R.drawable.bg1), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))
            Text(stringResource(if (state.mode == LoginMode.REGISTER) R.string.auth_create_account else R.string.auth_login),
                color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(40.dp))
            if (state.mode == LoginMode.REGISTER) {
                AuthField(state.username, stringResource(R.string.auth_username), false,
                    state.fieldErrors[LoginField.USERNAME], viewModel::updateUsername)
            }
            AuthField(state.email, stringResource(R.string.auth_email), false,
                state.fieldErrors[LoginField.EMAIL], viewModel::updateEmail, KeyboardType.Email)
            AuthField(state.password, stringResource(R.string.auth_password), true,
                state.fieldErrors[LoginField.PASSWORD], viewModel::updatePassword)
            if (state.mode == LoginMode.REGISTER) {
                AuthField(state.confirmPassword, stringResource(R.string.auth_confirm_password), true,
                    state.fieldErrors[LoginField.CONFIRM_PASSWORD], viewModel::updateConfirmation)
            }
            state.message?.let { message ->
                val text = when (message) {
                    LoginMessage.INVALID_CREDENTIALS -> stringResource(R.string.auth_invalid_credentials)
                    LoginMessage.EMAIL_EXISTS -> stringResource(R.string.auth_email_exists)
                    LoginMessage.NETWORK -> stringResource(R.string.auth_network_error)
                    LoginMessage.RATE_LIMITED -> stringResource(R.string.auth_rate_limited, state.retryAfterSeconds ?: 1)
                    LoginMessage.SERVICE -> stringResource(R.string.auth_service_error)
                }
                Text(text, color = Color(0xFFFF8A80), modifier = Modifier.padding(vertical = 12.dp))
            }
            Text(
                stringResource(if (state.mode == LoginMode.REGISTER) R.string.auth_have_account else R.string.auth_need_account),
                color = Color.White,
                modifier = Modifier.fillMaxWidth().clickable(enabled = !state.isSubmitting) { viewModel.toggleMode() }
                    .padding(vertical = 16.dp)
            )
            GradientButton(
                title = stringResource(if (state.mode == LoginMode.REGISTER) R.string.auth_register else R.string.auth_login),
                modifier = Modifier.fillMaxWidth().height(60.dp), enabled = !state.isSubmitting,
                onClick = viewModel::submit
            )
            if (state.isSubmitting) {
                CircularProgressIndicator(Modifier.padding(16.dp).semantics {
                    contentDescription = "authentication in progress"
                })
            }
        }
    }
}

@Composable
private fun AuthField(value: String, hint: String, password: Boolean, error: Int?,
                      onChange: (String) -> Unit, keyboardType: KeyboardType = KeyboardType.Password) {
    GradientTextField(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp), hint = hint, value = value,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        onValueChange = onChange
    )
    error?.let { Text(stringResource(it), color = Color(0xFFFF8A80), modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp)) }
    Spacer(Modifier.height(14.dp))
}

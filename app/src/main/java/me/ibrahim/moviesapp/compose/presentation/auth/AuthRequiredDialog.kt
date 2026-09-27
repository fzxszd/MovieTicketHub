package me.ibrahim.moviesapp.compose.presentation.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.ibrahim.moviesapp.compose.R

@Composable
fun AuthRequiredDialog(onDismiss: () -> Unit, onLogin: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auth_required_title)) },
        text = { Text(stringResource(R.string.auth_required_message)) },
        confirmButton = { TextButton(onClick = onLogin) { Text(stringResource(R.string.auth_login)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

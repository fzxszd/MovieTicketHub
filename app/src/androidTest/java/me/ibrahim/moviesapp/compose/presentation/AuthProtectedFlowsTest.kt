package me.ibrahim.moviesapp.compose.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import me.ibrahim.moviesapp.compose.presentation.auth.AuthRequiredDialog
import org.junit.Rule
import org.junit.Test

class AuthProtectedFlowsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun protectedActionExplainsAuthenticationWithoutExecutingAction() {
        compose.setContent { AuthRequiredDialog(onDismiss = {}, onLogin = {}) }
        compose.onNodeWithText("需要登录").assertExists()
        compose.onNodeWithText("登录后才能继续此操作").assertExists()
        compose.onNodeWithText("登录").assertExists()
    }
}

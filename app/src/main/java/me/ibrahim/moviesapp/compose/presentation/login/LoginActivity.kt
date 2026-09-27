package me.ibrahim.moviesapp.compose.presentation.login

import android.content.Intent
import androidx.compose.runtime.Composable
import me.ibrahim.moviesapp.compose.core.BaseActivity
import me.ibrahim.moviesapp.compose.presentation.main.MainActivity

class LoginActivity : BaseActivity() {
    @Composable
    override fun InitView() {
        LoginScreen(
            onLoginSuccess = {
                val source = intent.getStringExtra(EXTRA_SOURCE)
                if (source != null) {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_SOURCE, source))
                    finish()
                } else {
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }
            }
        )
    }

    companion object { const val EXTRA_SOURCE = "auth_source" }
}

package me.ibrahim.moviesapp.compose.domain.auth

data class AuthUser(
    val id: String,
    val email: String,
    val username: String,
    val avatarUri: String? = null
)

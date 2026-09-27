package me.ibrahim.moviesapp.compose.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val username: String,
    val email: String,
    val password: String,
    val confirmPassword: String
)

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class AuthUserDto(val id: String, val username: String, val email: String,
                       val avatarUri: String? = null)

@Serializable
data class AuthSuccessDto(val user: AuthUserDto, val token: String, val expiresAt: String)

@Serializable
data class AuthErrorDto(
    val code: String,
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
    val retryAfterSeconds: Int? = null
)

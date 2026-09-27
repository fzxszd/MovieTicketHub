package me.ibrahim.moviesapp.compose.data.network

import me.ibrahim.moviesapp.compose.data.dto.AuthSuccessDto
import me.ibrahim.moviesapp.compose.data.dto.AuthUserDto
import me.ibrahim.moviesapp.compose.domain.auth.AuthResult

interface AuthRemoteApi {
    suspend fun register(username: String, email: String, password: String,
                         confirmPassword: String): AuthResult<AuthSuccessDto>
    suspend fun login(email: String, password: String): AuthResult<AuthSuccessDto>
    suspend fun me(token: String): AuthResult<AuthUserDto>
    suspend fun logout(token: String): AuthResult<Unit>
}

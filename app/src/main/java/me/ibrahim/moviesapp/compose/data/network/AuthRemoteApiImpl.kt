package me.ibrahim.moviesapp.compose.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import me.ibrahim.moviesapp.compose.data.dto.AuthErrorDto
import me.ibrahim.moviesapp.compose.data.dto.AuthSuccessDto
import me.ibrahim.moviesapp.compose.data.dto.AuthUserDto
import me.ibrahim.moviesapp.compose.data.dto.LoginRequestDto
import me.ibrahim.moviesapp.compose.data.dto.RegisterRequestDto
import me.ibrahim.moviesapp.compose.domain.auth.AuthError
import me.ibrahim.moviesapp.compose.domain.auth.AuthResult

class AuthRemoteApiImpl(private val client: HttpClient) : AuthRemoteApi {
    override suspend fun register(username: String, email: String, password: String,
                                  confirmPassword: String): AuthResult<AuthSuccessDto> = request {
        client.post(url(RemoteApiEndpoints.AUTH_REGISTER)) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequestDto(username, email, password, confirmPassword))
        }
    }

    override suspend fun login(email: String, password: String): AuthResult<AuthSuccessDto> = request {
        client.post(url(RemoteApiEndpoints.AUTH_LOGIN)) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequestDto(email, password))
        }
    }

    override suspend fun me(token: String): AuthResult<AuthUserDto> = request {
        client.get(url(RemoteApiEndpoints.AUTH_ME)) { bearerAuth(token) }
    }

    override suspend fun logout(token: String): AuthResult<Unit> = request {
        client.post(url(RemoteApiEndpoints.AUTH_LOGOUT)) { bearerAuth(token) }
    }

    private fun url(path: String) = RemoteApiEndpoints.AUTH_BASE_URL + path

    private suspend inline fun <reified T> request(block: () -> HttpResponse): AuthResult<T> {
        return try {
            val response = block()
            if (response.status.value in 200..299) {
                if (T::class == Unit::class) {
                    @Suppress("UNCHECKED_CAST") AuthResult.Success(Unit as T)
                } else {
                    AuthResult.Success(response.body())
                }
            } else {
                val error = runCatching { response.body<AuthErrorDto>() }.getOrNull()
                AuthResult.Failure(mapError(response.status, error))
            }
        } catch (_: Exception) {
            AuthResult.Failure(AuthError.Network)
        }
    }

    private fun mapError(status: HttpStatusCode, body: AuthErrorDto?): AuthError = when (body?.code) {
        "validation_error" -> AuthError.Validation(body.fieldErrors)
        "email_exists" -> AuthError.EmailExists
        "invalid_credentials" -> AuthError.InvalidCredentials
        "rate_limited" -> AuthError.RateLimited(body.retryAfterSeconds ?: 1)
        "unauthenticated" -> AuthError.Unauthenticated
        else -> if (status == HttpStatusCode.Unauthorized) AuthError.Unauthenticated else AuthError.Service
    }
}

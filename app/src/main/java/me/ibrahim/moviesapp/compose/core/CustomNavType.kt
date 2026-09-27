package me.ibrahim.moviesapp.compose.core

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.ibrahim.moviesapp.compose.domain.Movie

object CustomNavType {
    // 定义一个通用的 Json 配置，确保序列化一致性
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val MovieType = object : NavType<Movie>(isNullableAllowed = false) {
        override fun get(bundle: Bundle, key: String): Movie? {
            return bundle.getString(key)?.let { json.decodeFromString(it) }
        }

        override fun parseValue(value: String): Movie {
            return json.decodeFromString(Uri.decode(value))
        }

        override fun put(bundle: Bundle, key: String, value: Movie) {
            bundle.putString(key, json.encodeToString(value))
        }

        override fun serializeAsValue(value: Movie): String {
            return Uri.encode(json.encodeToString(value))
        }
    }

}

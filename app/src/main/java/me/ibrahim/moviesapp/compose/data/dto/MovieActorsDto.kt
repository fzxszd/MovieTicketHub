package me.ibrahim.moviesapp.compose.data.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class MovieActorsDto(
    @SerialName("cast")
    val castList: List<Cast>? = null,
    @SerialName("id")
    val id: Int? = null
)

@Keep
@Serializable
data class Cast(
    @SerialName("cast_id")
    val castId: Int? = null,
    @SerialName("character")
    val character: String? = null,
    @SerialName("credit_id")
    val creditId: String? = null,
    @SerialName("id")
    val id: Int? = null,
    @SerialName("known_for_department")
    val knownForDepartment: String? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("original_name")
    val originalName: String? = null,
    @SerialName("profile_path")
    val profilePath: String? = null
)
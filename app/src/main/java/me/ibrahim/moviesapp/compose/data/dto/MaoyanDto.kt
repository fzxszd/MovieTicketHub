package me.ibrahim.moviesapp.compose.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class MaoyanMovieOnInfoResponseDto(
    @SerialName("movieList")
    val movieList: List<MaoyanMovieDto>? = null
)

@Serializable
data class MaoyanComingResponseDto(
    @SerialName("coming")
    val coming: List<MaoyanMovieDto>? = null
)

@Serializable
data class MaoyanDetailResponseDto(
    @SerialName("movie")
    val movie: MaoyanDetailDto? = null
)

@Serializable
data class MaoyanDetailDto(
    @SerialName("dra")
    val dra: String? = null,
    @SerialName("star")
    val star: String? = null,
    @SerialName("dir")
    val director: String? = null
)

@Serializable
data class MaoyanMovieDto(
    @SerialName("id")
    val id: Int,
    @SerialName("nm") // 电影名称
    val nm: String? = null,
    @SerialName("sc") // 评分
    val sc: JsonElement? = null,
    @SerialName("showInfo") // 上映信息
    val showInfo: String? = null,
    @SerialName("img") // 海报
    val img: String? = null,
    @SerialName("wish") // 想看人数
    val wish: JsonElement? = null,
    @SerialName("star") // 主演
    val star: String? = null,
    @SerialName("rt") // 上映日期
    val rt: String? = null,
    @SerialName("cat") // 电影分类
    val cat: String? = null
)

val MaoyanMovieDto.safeScore: Double
    get() = try {
        sc?.jsonPrimitive?.doubleOrNull ?: 0.0
    } catch (e: Exception) {
        0.0
    }

val MaoyanMovieDto.safeWish: Int
    get() = try {
        wish?.jsonPrimitive?.intOrNull ?: 0
    } catch (e: Exception) {
        0
    }

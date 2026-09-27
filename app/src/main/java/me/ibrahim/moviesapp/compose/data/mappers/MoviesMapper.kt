package me.ibrahim.moviesapp.compose.data.mappers

import me.ibrahim.moviesapp.compose.BuildConfig
import me.ibrahim.moviesapp.compose.data.database.*
import me.ibrahim.moviesapp.compose.data.dto.*
import me.ibrahim.moviesapp.compose.domain.Actor
import me.ibrahim.moviesapp.compose.domain.Movie

fun MovieDto.toMovie(): Movie {
    return Movie(
        backdropPath = "${BuildConfig.BACKDROP_IMAGES_BASEURL}$backdropPath",
        id = id,
        originalLanguage = originalLanguage,
        originalTitle = originalTitle,
        overview = overview,
        popularity = popularity,
        posterPath = "${BuildConfig.POSTER_IMAGES_BASEURL}$posterPath",
        releaseDate = releaseDate,
        title = title,
        video = video,
        voteAverage = voteAverage,
        voteCount = voteCount,
        showInfo = null,
        genres = genreIds?.mapNotNull { it?.let { id -> getGenreName(id) } } ?: emptyList()
    )
}

fun MaoyanMovieDto.toMovie(): Movie {
    val imageUrl = img?.replace("/w.h/", "/170.249/") ?: ""
    
    return Movie(
        backdropPath = imageUrl,
        id = id,
        originalLanguage = "zh",
        originalTitle = nm,
        overview = null,
        popularity = safeWish.toDouble(),
        posterPath = imageUrl,
        releaseDate = rt,
        title = nm,
        video = false,
        voteAverage = safeScore,
        voteCount = safeWish,
        showInfo = showInfo,
        genres = cat?.split(",")?.map { it.trim() } ?: emptyList()
    )
}

private fun getGenreName(id: Int): String {
    return when (id) {
        28 -> "动作"
        12 -> "冒险"
        16 -> "动画"
        35 -> "喜剧"
        80 -> "犯罪"
        99 -> "纪录"
        18 -> "剧情"
        10751 -> "家庭"
        14 -> "奇幻"
        36 -> "历史"
        27 -> "恐怖"
        10402 -> "音乐"
        9648 -> "悬疑"
        10749 -> "爱情"
        878 -> "科幻"
        10770 -> "电视"
        53 -> "惊悚"
        10752 -> "战争"
        37 -> "西部"
        else -> "电影"
    }
}

fun Movie.toMovieEntity(userEmail: String): MovieEntity {
    return MovieEntity(
        id = id,
        userEmail = userEmail,
        backdropPath = backdropPath,
        originalLanguage = originalLanguage,
        originalTitle = originalTitle,
        overview = overview,
        popularity = popularity,
        posterPath = posterPath,
        releaseDate = releaseDate,
        title = title,
        video = video,
        voteAverage = voteAverage,
        voteCount = voteCount
    )
}

fun MovieEntity.toMovie(): Movie {
    return Movie(
        backdropPath = backdropPath,
        id = id,
        originalLanguage = originalLanguage,
        originalTitle = originalTitle,
        overview = overview,
        popularity = popularity,
        posterPath = posterPath,
        releaseDate = releaseDate,
        title = title,
        video = video,
        voteAverage = voteAverage,
        voteCount = voteCount,
        showInfo = null,
        genres = emptyList<String>()
    )
}

fun Cast.toActor(): Actor {
    return Actor(
        castId = castId,
        character = character,
        creditId = creditId,
        id = id ?: -1,
        knownForDepartment = knownForDepartment,
        name = name,
        originalName = originalName,
        profilePath = profilePath
    )
}

fun MaoyanDetailDto.toActors(movieId: Int): List<Actor> {
    val directors = director.toCreditNames().mapIndexed { index, name ->
        name.toActor(
            movieId = movieId,
            role = "导演",
            department = "Directing",
            index = index
        )
    }
    val actors = star.toCreditNames().mapIndexed { index, name ->
        name.toActor(
            movieId = movieId,
            role = "主演",
            department = "Acting",
            index = index
        )
    }
    return (directors + actors).distinctBy { it.name }
}

private fun String?.toCreditNames(): List<String> {
    return this
        ?.split(',', '，')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        .orEmpty()
}

private fun String.toActor(
    movieId: Int,
    role: String,
    department: String,
    index: Int
): Actor {
    val stableCreditId = "maoyan-$movieId-$department-$index"
    return Actor(
        id = stableCreditId.hashCode(),
        creditId = stableCreditId,
        name = this,
        originalName = this,
        character = role,
        knownForDepartment = department,
        profilePath = null
    )
}

// 同步 DTO 转换逻辑
fun MovieEntity.toSyncDto() = MovieEntitySyncDto(
    id = id,
    backdropPath = backdropPath,
    originalLanguage = originalLanguage,
    originalTitle = originalTitle,
    overview = overview,
    popularity = popularity,
    posterPath = posterPath,
    releaseDate = releaseDate,
    title = title,
    video = video,
    voteAverage = voteAverage,
    voteCount = voteCount
)

fun OrderEntity.toSyncDto() = OrderEntitySyncDto(
    movieId = movieId,
    movieTitle = movieTitle,
    moviePoster = moviePoster,
    cinemaName = cinemaName,
    showTime = showTime,
    hallName = hallName,
    seatInfo = seatInfo,
    price = price,
    timestamp = timestamp
)

fun RatingEntity.toSyncDto() = RatingEntitySyncDto(
    movieId = movieId,
    rating = rating
)

fun CommentEntity.toSyncDto() = CommentEntitySyncDto(
    id = id,
    movieId = movieId,
    userEmail = userEmail,
    userName = userName,
    content = content,
    timestamp = timestamp,
    parentId = parentId,
    likes = likes
)

fun MovieEntitySyncDto.toEntity(email: String) = MovieEntity(
    id = id,
    userEmail = email,
    backdropPath = backdropPath ?: "",
    originalLanguage = originalLanguage ?: "zh",
    originalTitle = originalTitle ?: "",
    overview = overview ?: "",
    popularity = popularity ?: 0.0,
    posterPath = posterPath ?: "",
    releaseDate = releaseDate ?: "",
    title = title ?: "",
    video = video ?: false,
    voteAverage = voteAverage ?: 0.0,
    voteCount = voteCount ?: 0
)

fun OrderEntitySyncDto.toEntity(email: String) = OrderEntity(
    userEmail = email,
    movieId = movieId,
    movieTitle = movieTitle,
    moviePoster = moviePoster,
    cinemaName = cinemaName,
    showTime = showTime,
    hallName = hallName,
    seatInfo = seatInfo,
    price = price,
    timestamp = timestamp
)

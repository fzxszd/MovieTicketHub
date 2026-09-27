package me.ibrahim.moviesapp.compose

import me.ibrahim.moviesapp.compose.data.dto.MaoyanDetailDto
import me.ibrahim.moviesapp.compose.data.mappers.toActors
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun maoyanCredits_areMappedFromMovieDetail() {
        val credits = MaoyanDetailDto(
            star = "李绍哲,陈浩，立冬",
            director = "牟正洋"
        ).toActors(movieId = 1525868)

        assertEquals(listOf("牟正洋", "李绍哲", "陈浩", "立冬"), credits.map { it.name })
        assertEquals(listOf("导演", "主演", "主演", "主演"), credits.map { it.character })
        assertEquals(listOf("Directing", "Acting", "Acting", "Acting"), credits.map { it.knownForDepartment })
    }
}

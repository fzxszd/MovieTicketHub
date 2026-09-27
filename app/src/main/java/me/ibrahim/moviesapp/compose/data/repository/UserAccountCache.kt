package me.ibrahim.moviesapp.compose.data.repository

import me.ibrahim.moviesapp.compose.data.database.MoviesDao
import me.ibrahim.moviesapp.compose.data.database.UserEntity
import me.ibrahim.moviesapp.compose.domain.auth.AuthUser

interface UserAccountCache { suspend fun save(user: AuthUser) }

class RoomUserAccountCache(private val moviesDao: MoviesDao) : UserAccountCache {
    override suspend fun save(user: AuthUser) {
        moviesDao.upsertUser(UserEntity(user.email, user.id, user.username, user.avatarUri))
    }
}

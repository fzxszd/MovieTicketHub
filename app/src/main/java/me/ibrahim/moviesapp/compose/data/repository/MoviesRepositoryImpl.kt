package me.ibrahim.moviesapp.compose.data.repository

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.ibrahim.moviesapp.compose.data.database.*
import me.ibrahim.moviesapp.compose.data.dto.*
import me.ibrahim.moviesapp.compose.data.mappers.*
import me.ibrahim.moviesapp.compose.data.network.MoviesRemoteApi
import me.ibrahim.moviesapp.compose.data.network.RemoteApiEndpoints
import me.ibrahim.moviesapp.compose.domain.Actor
import me.ibrahim.moviesapp.compose.domain.DataError
import me.ibrahim.moviesapp.compose.domain.Movie
import me.ibrahim.moviesapp.compose.domain.MoviesRepository
import me.ibrahim.moviesapp.compose.domain.Result
import me.ibrahim.moviesapp.compose.domain.map
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository
import me.ibrahim.moviesapp.compose.domain.auth.AuthState
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class MoviesRepositoryImpl(
    private val moviesRemoteApi: MoviesRemoteApi,
    private val moviesDao: MoviesDao,
    private val httpClient: HttpClient,
    private val authRepository: AuthRepository
) : MoviesRepository {

    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    private var lastSyncedData: UserSyncData? = null
    private val _nowPlayingMovies = MutableStateFlow<List<Movie>>(emptyList())

    // --- 推荐系统共享状态实现 ---
    

    

    /* legacy client recommendation removed; server RecommendationRepository is authoritative
    private val currentUser = authRepository.state.map { state ->
        (state as? AuthState.Authenticated)?.user
    }.distinctUntilChanged()

    override val recommendedMovies: Flow<List<Movie>> = combine(
        _nowPlayingMovies,
        flowOf<List<Movie>>(emptyList()),
        currentUser.flatMapLatest { user ->
            if (user != null) getAllOrders(user.email) else flowOf(emptyList())
        },
        _recWeights,
        _refreshTrigger
    ) { movies, _, orders, weights, trigger ->
        if (movies.isEmpty()) emptyList()
        else calculateRecommendations(movies, emptyList(), orders, weights.first, weights.second, weights.third, trigger)
    }.flowOn(Dispatchers.Default)

    override fun updateRecWeights(pref: Float, pop: Float, fresh: Float) {
        _recWeights.value = Triple(pref, pop, fresh)
    }

    override fun triggerRecRefresh() {
        syncScope.launch {
            _isRecLoading.value = true
            delay(500)
            _refreshTrigger.value = System.currentTimeMillis()
            _isRecLoading.value = false
        }
    }

    private fun calculateRecommendations(
        allMovies: List<Movie>,
        _favorites: List<Movie>,
        orders: List<OrderEntity>,
        wPref: Float,
        wPop: Float,
        wFresh: Float,
        seed: Long
    ): List<Movie> {
        val purchasedMovieIds = orders.map { it.movieId }.toSet()

        return allMovies
            .filter { movie ->
                !purchasedMovieIds.contains(movie.id)
            }
            .map { movie ->
                val movieRandom = Random(movie.id.toLong() + seed)
                val prefScore = 0.1f + movieRandom.nextFloat() * 0.9f
                val popScore = minOf((movie.popularity?.toFloat() ?: 0f) / 400f, 1.0f)
                val isNew = movie.releaseDate?.let { it.contains("2024") || it.contains("2025") } ?: false
                val freshnessScore = if (isNew) 1.0f else 0.1f
                val noise = if (seed != 0L) (movieRandom.nextFloat() * 0.05f) else 0f

                val totalScore = (prefScore * wPref) + (popScore * wPop) + (freshnessScore * wFresh) + noise
                movie to totalScore
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(10)
    }

    */
    override suspend fun fetchNowPlayingMovies(): Result<List<Movie>, DataError.Remote> {
        return moviesRemoteApi.fetchNowPlayingMovies()
            .map { response ->
                val movies = response.movieList?.map { maoyanDto ->
                    maoyanDto.toMovie()
                } ?: emptyList()
                _nowPlayingMovies.value = movies // 更新缓存以触发推荐计算
                movies
            }
    }

    override suspend fun fetchUpcomingMovies(): Result<List<Movie>, DataError.Remote> {
        return moviesRemoteApi.fetchUpcomingMovies()
            .map { response ->
                response.coming?.map { maoyanDto ->
                    maoyanDto.toMovie()
                } ?: emptyList()
            }
    }

    override suspend fun fetchMovieActors(movieId: Int): Result<List<Actor>, DataError.Remote> {
        return moviesRemoteApi.fetchMovieDetail(movieId = movieId)
            .map { response ->
                response.movie?.toActors(movieId).orEmpty()
            }
    }

    override suspend fun fetchMovieDetail(movieId: Int): Result<String, DataError.Remote> {
        return moviesRemoteApi.fetchMovieDetail(movieId = movieId)
            .map { response ->
                response.movie?.dra ?: ""
            }
    }

    // Public profile cache only; authentication is owned by AuthRepository.
    override suspend fun updateUserProfile(user: UserEntity) {
        moviesDao.upsertUser(user)
        pushDataToPC(user.email)
    }

    override suspend fun clearAllData() {
        stopAutoSync()
        moviesDao.deleteAllFavorites()
        moviesDao.deleteAllOrders()
        moviesDao.deleteAllRatings()
        moviesDao.deleteAllLikes()
        moviesDao.deleteAllComments()
        moviesDao.deleteAllUsers()
        lastSyncedData = null
    }

    override fun startAutoSync(email: String) {
        syncJob?.cancel() 
        syncJob = syncScope.launch {
            while (isActive) {
                delay(30_000) 
                Log.d("SyncDebug", "⏰ [AutoSync] 定时同步触发")
                pushDataToPC(email)
            }
        }
    }

    override fun stopAutoSync() {
        syncJob?.cancel()
        syncJob = null
        lastSyncedData = null
    }

    // database operations
    override fun getAllOrders(userEmail: String): Flow<List<OrderEntity>> {
        return moviesDao.getAllOrders(userEmail)
    }

    override suspend fun deleteOrder(order: OrderEntity) {
        moviesDao.deleteOrder(order)
        pushDataToPC(order.userEmail)
    }


    override fun hasPurchasedMovie(movieId: Int, userEmail: String): Flow<Boolean> {
        return moviesDao.hasPurchasedMovie(movieId, userEmail)
    }

    override suspend fun saveRating(movieId: Int, userEmail: String, rating: Int) {
        moviesDao.upsertRating(RatingEntity(movieId, userEmail, rating))
        pushDataToPC(userEmail)
    }

    override fun getRating(movieId: Int, userEmail: String): Flow<Int?> {
        return moviesDao.getMovieRating(movieId, userEmail)
    }

    override suspend fun saveComment(movieId: Int, userEmail: String, userName: String, content: String, parentId: Int?) {
        val uniqueId = Random.nextInt(Int.MAX_VALUE)
        moviesDao.upsertComment(
            CommentEntity(
                id = uniqueId,
                movieId = movieId,
                userEmail = userEmail,
                userName = userName,
                content = content,
                parentId = parentId,
                timestamp = System.currentTimeMillis()
            )
        )
        pushDataToPC(userEmail)
    }

    override fun getComments(movieId: Int): Flow<List<CommentEntity>> {
        return moviesDao.getCommentsForMovie(movieId)
    }

    override suspend fun toggleCommentLike(commentId: Int, userEmail: String) {
        val isLiked = moviesDao.isCommentLikedByUser(commentId, userEmail).first()
        if (isLiked) {
            moviesDao.deleteLike(CommentLikeEntity(commentId, userEmail))
            moviesDao.decrementLikes(commentId)
        } else {
            moviesDao.upsertLike(CommentLikeEntity(commentId, userEmail))
            moviesDao.incrementLikes(commentId)
        }
        pushDataToPC(userEmail)
    }

    override fun isCommentLiked(commentId: Int, userEmail: String): Flow<Boolean> {
        return moviesDao.isCommentLikedByUser(commentId, userEmail)
    }

    override fun getLikedCommentIds(userEmail: String): Flow<Set<Int>> {
        return moviesDao.getLikedCommentIds(userEmail).map { it.toSet() }
    }

    override suspend fun pushDataToPC(email: String) {
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                val token = authRepository.currentToken() ?: return@withContext
                if (authRepository.currentUser()?.email != email) return@withContext
                val user = moviesDao.getUserByEmail(email) ?: return@withContext
                
                val orders = moviesDao.getAllOrders(email).first()
                val ratings = moviesDao.getAllRatings(email)
                val userComments = moviesDao.getAllComments(email)
                val globalComments = moviesDao.getGlobalAllComments() 
                val likedCommentIds = moviesDao.getLikedCommentIds(email).first()
                
                val currentData = UserSyncData(
                    name = user.name,
                    avatarUri = user.avatarUri,
                    orders = orders.map { it.toSyncDto() },
                    ratings = ratings.map { it.toSyncDto() },
                    comments = userComments.map { it.toSyncDto() },
                    globalComments = globalComments.map { it.toSyncDto() },
                    likedCommentIds = likedCommentIds
                )

                if (currentData == lastSyncedData) return@withContext

                val response: HttpResponse = httpClient.post(RemoteApiEndpoints.AUTH_BASE_URL + RemoteApiEndpoints.SYNC_PUSH) {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(currentData)
                }
                
                if (response.status == HttpStatusCode.Unauthorized) {
                    authRepository.invalidateSession()
                    stopAutoSync()
                } else if (response.status.isSuccess()) {
                    lastSyncedData = currentData
                    Log.d("SyncDebug", "✅ [PUSH] 同步成功")
                }
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ [PUSH] 失败: ${e.message}")
            }
        }
    }

    override suspend fun pullDataFromPC(email: String) {
        pullDataFromPCInternal(email)
    }

    private suspend fun pullDataFromPCInternal(email: String) {
        withContext(Dispatchers.IO) {
            try {
                val token = authRepository.currentToken() ?: return@withContext
                val authUser = authRepository.currentUser() ?: return@withContext
                if (authUser.email != email) return@withContext
                Log.d("SyncDebug", "⬇️ [PULL] 开始拉取并清理旧数据...")
                val httpResponse: HttpResponse = httpClient.get(RemoteApiEndpoints.AUTH_BASE_URL + RemoteApiEndpoints.SYNC_PULL) {
                    bearerAuth(token)
                }
                if (httpResponse.status == HttpStatusCode.Unauthorized) {
                    authRepository.invalidateSession()
                    stopAutoSync()
                    return@withContext
                }
                if (!httpResponse.status.isSuccess()) return@withContext

                val response: UserSyncData = httpResponse.body()

                moviesDao.deleteOrdersByUser(email)
                moviesDao.deleteRatingsByUser(email)
                moviesDao.deleteLikesByUser(email)

                moviesDao.upsertUser(UserEntity(
                    email = authUser.email,
                    userId = authUser.id,
                    name = response.name,
                    avatarUri = response.avatarUri
                ))

                // Legacy sync payload never writes the authoritative favorites table.
                
                response.orders.forEach {
                    moviesDao.upsertOrder(it.toEntity(email))
                }

                response.ratings.forEach {
                    moviesDao.upsertRating(RatingEntity(
                        movieId = it.movieId,
                        userEmail = email,
                        rating = it.rating
                    ))
                }
                
                response.globalComments.forEach {
                    moviesDao.upsertComment(CommentEntity(
                        id = it.id,
                        movieId = it.movieId,
                        userEmail = it.userEmail,
                        userName = it.userName,
                        content = it.content,
                        timestamp = it.timestamp,
                        parentId = it.parentId,
                        likes = it.likes
                    ))
                }

                response.likedCommentIds.forEach { commentId ->
                    moviesDao.upsertLike(CommentLikeEntity(commentId, email))
                }
                
                lastSyncedData = response
                Log.d("SyncDebug", "✅ [PULL] 数据恢复完成")
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ [PULL] 崩溃原因: ${e.message}")
            }
        }
    }
}

package me.ibrahim.moviesapp.compose.data.repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.ibrahim.moviesapp.compose.data.dto.RecommendationEventDto
import me.ibrahim.moviesapp.compose.data.mappers.toDomain
import me.ibrahim.moviesapp.compose.data.network.*
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository
import me.ibrahim.moviesapp.compose.domain.recommendation.*
import java.util.UUID
class RecommendationRepositoryImpl(private val api:RecommendationRemoteApi,private val auth:AuthRepository):RecommendationRepository{
 private val _state=MutableStateFlow<RecommendationState>(RecommendationState.Loading);override val state=_state.asStateFlow()
 override suspend fun refresh(limit:Int){_state.value=RecommendationState.Loading;when(val r=api.get(auth.currentToken(),limit)){is RecommendationApiResult.Success->runCatching{r.value.toDomain()}.onSuccess{_state.value=RecommendationState.Content(it)}.onFailure{_state.value=RecommendationState.Error()};is RecommendationApiResult.Failure->_state.value=RecommendationState.Error(r.code=="NETWORK")}}
 override suspend fun reportEvent(recommendationId:String,movieId:Int,action:String){api.event(auth.currentToken(),recommendationId,RecommendationEventDto(UUID.randomUUID().toString(),movieId,action))}
 override fun clearAccount(){_state.value=RecommendationState.Loading}
}

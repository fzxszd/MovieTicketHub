package me.ibrahim.moviesapp.compose.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.ibrahim.moviesapp.compose.data.database.UserEntity
import me.ibrahim.moviesapp.compose.domain.MoviesRepository
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository
import me.ibrahim.moviesapp.compose.domain.auth.AuthState
import me.ibrahim.moviesapp.compose.domain.favorite.FavoriteRepository
import me.ibrahim.moviesapp.compose.domain.order.OrderRepository
import me.ibrahim.moviesapp.compose.domain.order.TicketSummary
import me.ibrahim.moviesapp.compose.domain.seat.SeatResult
import me.ibrahim.moviesapp.compose.presentation.payment.PaymentAccountStore
import java.io.File
import java.io.FileOutputStream

data class UserProfile(val name: String = "游客", val email: String = "", val avatarUri: String? = null)

fun interface ProfileImageStore { fun save(uri: Uri): String? }

class AndroidProfileImageStore(private val context: Context) : ProfileImageStore {
    override fun save(uri: Uri): String? = try {
        val file = File(context.filesDir, "profile_avatar_${System.currentTimeMillis()}.png")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { input.copyTo(it) }
        }
        file.absolutePath
    } catch (_: Exception) { null }
}

class SettingsViewModel(
    private val repository: MoviesRepository,
    private val authRepository: AuthRepository,
    private val profileImageStore: ProfileImageStore,
    private val favoriteRepository: FavoriteRepository? = null,
    private val orderRepository: OrderRepository? = null,
    private val paymentAccountStore: PaymentAccountStore? = null
) : ViewModel() {
    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()
    private val _tickets = MutableStateFlow<List<TicketSummary>>(emptyList())
    val tickets: StateFlow<List<TicketSummary>> = _tickets.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.state.collectLatest { state ->
                val user = (state as? AuthState.Authenticated)?.user
                _userProfile.value = user?.let { UserProfile(it.username, it.email, it.avatarUri) } ?: UserProfile()
                refreshTicketsFor(user != null)
            }
        }
    }

    /** Refreshes tickets whenever the settings screen becomes visible again. */
    fun refreshTickets() {
        viewModelScope.launch {
            refreshTicketsFor(authRepository.currentUser() != null)
        }
    }

    private suspend fun refreshTicketsFor(isAuthenticated: Boolean) {
        if (!isAuthenticated) {
            _tickets.value = emptyList()
            return
        }
        when (val result = orderRepository?.listTickets()) {
            is SeatResult.Success -> _tickets.value = result.value
            else -> _tickets.value = emptyList()
        }
    }

    fun saveImageToInternalStorage(uri: Uri): String? = profileImageStore.save(uri)

    fun updateUserProfile(name: String, email: String, avatarUri: String?) {
        val user = authRepository.currentUser() ?: return
        viewModelScope.launch {
            repository.updateUserProfile(UserEntity(email, user.id, name, avatarUri))
            _userProfile.value = UserProfile(name, email, avatarUri)
        }
    }

    fun refundTicket(ticket: TicketSummary, onResult: (Boolean) -> Unit = {}) {
        val repo = orderRepository ?: return onResult(false)
        viewModelScope.launch {
            val result = repo.refund(ticket.orderId)
            if (result is SeatResult.Success) {
                paymentAccountStore?.creditForRefund(ticket.orderId, ticket.totalMinor)
                _tickets.value = _tickets.value.filterNot { it.id == ticket.id }
                onResult(true)
            } else onResult(false)
        }
    }
    fun logout(onComplete: () -> Unit) {
        if (authRepository.state.value !is AuthState.Authenticated) { onComplete(); return }
        viewModelScope.launch { val id = authRepository.currentUser()?.id; authRepository.logout(); id?.let { favoriteRepository?.onAccountStopped(it, true) }; repository.stopAutoSync(); onComplete() }
    }
    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch { val id = authRepository.currentUser()?.id; authRepository.logout(); id?.let { favoriteRepository?.onAccountStopped(it, true) }; repository.clearAllData(); onComplete() }
    }
}

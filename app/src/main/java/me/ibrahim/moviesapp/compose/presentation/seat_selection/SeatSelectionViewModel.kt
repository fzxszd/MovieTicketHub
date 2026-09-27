package me.ibrahim.moviesapp.compose.presentation.seat_selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import android.os.SystemClock
import java.time.Instant
import java.util.UUID
import me.ibrahim.moviesapp.compose.domain.seat.*
import me.ibrahim.moviesapp.compose.domain.order.OrderRepository

class SeatSelectionViewModel(private val repository: SeatRepository, private val orderRepository: OrderRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SeatSelectionState())
    val uiState = _uiState.asStateFlow()
    private var showtimeId = ""
    private var idempotencyKey: String? = null
    private var pollingJob: Job? = null
    private var countdownJob: Job? = null
    private var countdownBaseElapsed: Long = 0L
    private var countdownBaseSeconds: Long = 0L

    /** Compatibility entry point for the old screen; time is now the stable showtime ID. */
    fun loadOccupiedSeats(movieId: Int, cinema: String, time: String, hall: String) = load(time)
    fun setTicketPrice(price: String) = Unit // Prices are now authoritative per-seat values.

    fun load(id: String = showtimeId) {
        if (id.isBlank()) return
        showtimeId = id
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            when (val result = repository.snapshot(id)) {
                is SeatResult.Success -> applySnapshot(result.value)
                is SeatResult.Failure -> _uiState.update { it.copy(loading = false, seats = emptyList(), error = result.code) }
            }
        }
    }

    /** Poll only while this ViewModel is alive; every response remains server-authoritative. */
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) { delay(5_000); if (showtimeId.isNotBlank()) load() }
        }
    }
    fun stopPolling() { pollingJob?.cancel(); pollingJob = null }

    fun onSeatClick(seatId: String) {
        _uiState.update { state ->
            val seat = state.seats.firstOrNull { it.id == seatId } ?: return@update state
            when {
                seatId in state.selectedIds -> state.copy(selectedIds = state.selectedIds - seatId, seats = state.seats.map { if (it.id == seatId) it.copy(status = SeatStatus.AVAILABLE) else it }).recalculate()
                seat.status != SeatStatus.AVAILABLE -> state
                state.selectedIds.size >= 6 -> state.copy(error = "MAX_SEATS")
                else -> state.copy(selectedIds = state.selectedIds + seatId, seats = state.seats.map { if (it.id == seatId) it.copy(status = SeatStatus.SELECTED) else it }).recalculate()
            }
        }
    }

    fun lockSelected(onLocked: (SeatLock) -> Unit) {
        val state = _uiState.value
        if (state.selectedIds.isEmpty() || state.locking || showtimeId.isBlank()) return
        val key = idempotencyKey ?: "seat-${UUID.randomUUID()}".also { idempotencyKey = it }
        viewModelScope.launch {
            _uiState.update { it.copy(locking = true, error = null) }
            when (val result = repository.lock(showtimeId, state.selectedIds.toList(), state.inventoryVersion, key)) {
                is SeatResult.Success -> {
                    _uiState.update { it.copy(locking = false, activeLock = result.value) }
                    onLocked(result.value)
                    beginCountdown(result.value)
                }
                is SeatResult.Failure -> { _uiState.update { it.copy(locking = false, error = result.code) }; load() }
            }
        }
    }

    fun releaseActiveLock() { _uiState.value.activeLock?.let { lock -> viewModelScope.launch {
        repository.release(lock.id); countdownJob?.cancel(); idempotencyKey = null
        _uiState.update { it.copy(activeLock = null, remainingLockSeconds = 0L) }; load()
    } } }

    /** Open payment with the active lock. PaymentScreen creates/loads the order. */
    fun continueToPayment(onLockReady: (String) -> Unit) {
        val lock = _uiState.value.activeLock ?: return
        onLockReady(lock.id)
    }

    fun revalidateActiveLock(onActive: (SeatLock) -> Unit) {
        val lock = _uiState.value.activeLock ?: return
        viewModelScope.launch {
            when (val result = repository.getLock(lock.id)) {
                is SeatResult.Success -> if (result.value.status == SeatLockStatus.ACTIVE) {
                    beginCountdown(result.value); _uiState.update { it.copy(activeLock = result.value) }; onActive(result.value)
                } else invalidateActiveLock()
                is SeatResult.Failure -> invalidateActiveLock()
            }
        }
    }

    fun onForeground() { load(); _uiState.value.activeLock?.let { revalidateActiveLock {} } }
    override fun onCleared() { stopPolling(); countdownJob?.cancel(); super.onCleared() }

    private fun beginCountdown(lock: SeatLock) {
        val remaining = runCatching { Instant.parse(lock.expiresAt).epochSecond - Instant.parse(lock.serverTime).epochSecond }.getOrDefault(0L).coerceAtLeast(0)
        countdownBaseSeconds = remaining; countdownBaseElapsed = elapsedRealtime()
        countdownJob?.cancel(); countdownJob = viewModelScope.launch {
            while (true) {
                val seconds = (countdownBaseSeconds - ((elapsedRealtime() - countdownBaseElapsed) / 1000)).coerceAtLeast(0)
                _uiState.update { it.copy(remainingLockSeconds = seconds) }
                if (seconds == 0L) { invalidateActiveLock(); return@launch }
                delay(1_000)
            }
        }
    }
    private fun elapsedRealtime(): Long = runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }
    private fun invalidateActiveLock() { countdownJob?.cancel(); _uiState.update { it.copy(activeLock = null, remainingLockSeconds = 0L, error = "LOCK_UNAVAILABLE") }; load() }

    private fun applySnapshot(snapshot: SeatLayoutSnapshot) {
        _uiState.update { old ->
            val seats = snapshot.rows.flatMap { row -> row.positions.mapNotNull { p ->
                p.seatId?.let { id -> Seat(id, p.rowIndex, p.columnIndex, p.rowLabel, p.seatLabel, p.price?.amountMinor ?: 0, when (p.inventoryStatus) {
                    SeatInventoryStatus.AVAILABLE -> SeatStatus.AVAILABLE; SeatInventoryStatus.LOCKED_BY_ME -> SeatStatus.LOCKED_BY_ME
                    SeatInventoryStatus.LOCKED_BY_OTHER -> SeatStatus.RESERVED; SeatInventoryStatus.SOLD -> SeatStatus.RESERVED
                    else -> SeatStatus.UNAVAILABLE
                }) }
            } }
            val allowedSelection = old.selectedIds.intersect(seats.filter { it.status == SeatStatus.AVAILABLE }.map { it.id }.toSet())
            old.copy(seats = seats.map { if (it.id in allowedSelection && it.status == SeatStatus.AVAILABLE) it.copy(status = SeatStatus.SELECTED) else it }, selectedIds = allowedSelection, layout = snapshot, inventoryVersion = snapshot.inventoryVersion, loading = false).recalculate()
        }
    }
}

data class SeatSelectionState(val seats: List<Seat> = emptyList(), val layout: SeatLayoutSnapshot? = null, val selectedIds: Set<String> = emptySet(), val inventoryVersion: Long = 0, val loading: Boolean = false, val locking: Boolean = false, val error: String? = null, val activeLock: SeatLock? = null, val remainingLockSeconds: Long = 0L, val selectedCount: Int = 0, val totalPrice: Double = 0.0) {
    fun recalculate() = copy(selectedCount = selectedIds.size, totalPrice = seats.filter { it.id in selectedIds }.sumOf { it.priceMinor } / 100.0)
}
data class Seat(val id: String, val row: Int, val column: Int, val rowLabel: String, val seatLabel: String?, val priceMinor: Long, val status: SeatStatus)
enum class SeatStatus { AVAILABLE, SELECTED, RESERVED, LOCKED_BY_ME, UNAVAILABLE }

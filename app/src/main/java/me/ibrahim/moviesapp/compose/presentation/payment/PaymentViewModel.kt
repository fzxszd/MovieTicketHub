package me.ibrahim.moviesapp.compose.presentation.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ibrahim.moviesapp.compose.domain.order.*
import me.ibrahim.moviesapp.compose.domain.seat.SeatRepository
import me.ibrahim.moviesapp.compose.domain.seat.SeatResult
import java.util.UUID

data class PaymentState(val loading: Boolean = false, val order: ServerOrder? = null, val attempt: PaymentAttempt? = null, val error: String? = null, val processing: Boolean = false, val accounts: List<PaymentAccount> = emptyList(), val selectedMethod: String = "ALIPAY_SIMULATED", val confirmed: Boolean = false)

class PaymentViewModel(
    private val orders: OrderRepository,
    private val seatRepository: SeatRepository,
    private val accountStore: PaymentAccountStore? = null
) : ViewModel() {
    private val _state = MutableStateFlow(PaymentState())
    val state = _state.asStateFlow()
    private var payKey: String? = null
    private var activeLockId: String? = null
    private var releasing = false

    fun selectMethod(method: String) { _state.update { it.copy(selectedMethod = method) } }

    fun loadOrder(orderId: String) = viewModelScope.launch {
        _state.value = PaymentState(loading = true, accounts = accountStore?.accounts().orEmpty())
        payKey = null
        when (val result = orders.getOrder(orderId)) {
            is SeatResult.Success -> _state.update { it.copy(order = result.value, loading = false, confirmed = result.value.status == "PAID") }
            is SeatResult.Failure -> _state.update { it.copy(loading = false, error = result.code) }
        }
    }

    fun createFromLock(lockId: String) = viewModelScope.launch {
        activeLockId = lockId
        _state.value = PaymentState(loading = true, accounts = accountStore?.accounts().orEmpty())
        payKey = null
        when (val quote = orders.quote(lockId)) {
            is SeatResult.Success -> when (val order = orders.create(lockId, quote.value.quoteVersion, "order-create-$lockId")) {
                is SeatResult.Success -> _state.update { it.copy(order = order.value, loading = false, confirmed = order.value.status == "PAID") }
                is SeatResult.Failure -> failAndRelease(order.code)
            }
            is SeatResult.Failure -> failAndRelease(quote.code)
        }
    }

    fun pay(method: String = _state.value.selectedMethod) {
        val order = _state.value.order ?: return
        if (_state.value.processing || _state.value.confirmed) return
        val account = _state.value.accounts.firstOrNull { it.id == method }
        if (accountStore != null && (account == null || !account.enabled || account.balanceMinor < order.totalMinor)) {
            _state.update { it.copy(error = "余额不足", selectedMethod = method) }
            releaseUnpaidLock()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(processing = true, error = null, selectedMethod = method) }
            val key = payKey ?: UUID.randomUUID().toString().also { payKey = it }
            when (val attempt = orders.pay(order.id, method, key)) {
                is SeatResult.Success -> {
                    _state.update { it.copy(attempt = attempt.value) }
                    when (val confirmation = orders.confirmPayment(order.id, attempt.value.id)) {
                        is SeatResult.Success -> {
                            val result = confirmation.value
                            if (result.orderStatus == "PAID" && result.ticketReady) {
                                accountStore?.debit(method, order.totalMinor, attempt.value.id, order.id)
                                _state.update { it.copy(processing = false, confirmed = true) }
                                loadOrder(order.id)
                            } else {
                                _state.update { it.copy(processing = false, error = "支付未成功，座位已释放") }
                                releaseUnpaidLock()
                                refresh()
                            }
                        }
                        is SeatResult.Failure -> {
                            _state.update { it.copy(processing = false, error = "支付失败，座位已释放") }
                            releaseUnpaidLock()
                            refresh()
                        }
                    }
                }
                is SeatResult.Failure -> {
                    _state.update { it.copy(processing = false, error = "支付失败，座位已释放") }
                    releaseUnpaidLock()
                    refresh()
                }
            }
        }
    }

    /** Called when the user leaves payment without completing payment. */
    fun abandonUnpaidPayment(onFinished: () -> Unit) {
        if (_state.value.confirmed || activeLockId == null) {
            onFinished()
            return
        }
        val lockId = activeLockId ?: return onFinished()
        if (releasing) return
        releasing = true
        viewModelScope.launch {
            seatRepository.release(lockId)
            activeLockId = null
            releasing = false
            onFinished()
        }
    }

    fun refresh() { _state.value.order?.id?.let(::loadOrder) }

    private fun failAndRelease(code: String) {
        _state.update { it.copy(loading = false, error = code) }
        releaseUnpaidLock()
    }

    private fun releaseUnpaidLock() {
        val lockId = activeLockId ?: return
        if (releasing || _state.value.confirmed) return
        releasing = true
        viewModelScope.launch {
            seatRepository.release(lockId)
            activeLockId = null
            releasing = false
        }
    }

    @Deprecated("Purchases are server authoritative")
    fun saveOrder(movieId: Int, movieTitle: String, moviePoster: String?, cinemaName: String, showTime: String, hallName: String, seatInfo: String, price: String) = Unit
}

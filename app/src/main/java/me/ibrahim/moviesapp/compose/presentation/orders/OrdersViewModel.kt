package me.ibrahim.moviesapp.compose.presentation.orders
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ibrahim.moviesapp.compose.domain.order.*
import me.ibrahim.moviesapp.compose.domain.seat.SeatResult
data class OrdersState(val loading:Boolean=false,val orders:List<ServerOrder> = emptyList(),val error:String?=null)
class OrdersViewModel(private val repository:OrderRepository):ViewModel(){private val _state=MutableStateFlow(OrdersState());val state=_state.asStateFlow();fun refresh(){viewModelScope.launch{_state.value=OrdersState(loading=true);when(val r=repository.listOrders()){is SeatResult.Success->_state.value=OrdersState(orders=r.value);is SeatResult.Failure->_state.value=OrdersState(error=r.code)}}};init{refresh()}}

package me.ibrahim.moviesapp.compose.presentation.orders
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ibrahim.moviesapp.compose.domain.order.*
import me.ibrahim.moviesapp.compose.domain.seat.SeatResult
class OrderDetailViewModel(private val repo:OrderRepository):ViewModel(){private val _state=MutableStateFlow<ServerOrder?>(null);val state=_state.asStateFlow();val error=MutableStateFlow<String?>(null);fun load(id:String){viewModelScope.launch{when(val r=repo.getOrder(id)){is SeatResult.Success->_state.value=r.value;is SeatResult.Failure->error.value=r.code}}}}

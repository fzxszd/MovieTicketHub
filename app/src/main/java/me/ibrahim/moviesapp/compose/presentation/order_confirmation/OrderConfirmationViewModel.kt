package me.ibrahim.moviesapp.compose.presentation.order_confirmation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ibrahim.moviesapp.compose.domain.order.*
import me.ibrahim.moviesapp.compose.domain.seat.SeatResult
import java.util.UUID

data class OrderConfirmationState(val loading:Boolean=false,val creating:Boolean=false,val quote:OrderQuote?=null,val order:ServerOrder?=null,val error:String?=null,val amountChanged:Boolean=false)
class OrderConfirmationViewModel(private val orders:OrderRepository):ViewModel(){
 private val _state=MutableStateFlow(OrderConfirmationState());val state=_state.asStateFlow();private var createKey:String?=null
 fun load(lockId:String){viewModelScope.launch{_state.value=OrderConfirmationState(loading=true);when(val r=orders.quote(lockId)){is SeatResult.Success->_state.value=OrderConfirmationState(quote=r.value);is SeatResult.Failure->_state.value=OrderConfirmationState(error=r.code)}}}
 fun verify(lockId:String)=load(lockId)
 fun confirm(){val quote=_state.value.quote?:return; if(_state.value.creating)return;viewModelScope.launch{_state.value=_state.value.copy(creating=true,error=null);val key=createKey?:UUID.randomUUID().toString().also{createKey=it};when(val r=orders.create(quote.lockId,quote.quoteVersion,key)){is SeatResult.Success->_state.value=_state.value.copy(creating=false,order=r.value);is SeatResult.Failure->_state.value=_state.value.copy(creating=false,error=r.code,amountChanged=r.code.contains("AMOUNT"))}}}
}

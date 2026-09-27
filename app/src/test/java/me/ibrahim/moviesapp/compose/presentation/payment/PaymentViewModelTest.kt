package me.ibrahim.moviesapp.compose.presentation.payment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.ibrahim.moviesapp.compose.domain.cinema.Money
import me.ibrahim.moviesapp.compose.domain.order.*
import me.ibrahim.moviesapp.compose.domain.seat.*
import org.junit.*
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest{
 private val dispatcher=UnconfinedTestDispatcher()
 @Before fun setUp(){Dispatchers.setMain(dispatcher)}
 @After fun tearDown(){Dispatchers.resetMain()}
 @Test fun payment_uses_server_processing_state()=runBlocking{val vm=PaymentViewModel(FakePaymentRepo(), FakeSeatRepo());vm.loadOrder("o");vm.pay("ALIPAY_SIMULATED");Assert.assertEquals("PAYMENT_PROCESSING",vm.state.value.order?.status)}
}
private class FakeSeatRepo: SeatRepository {
 override suspend fun snapshot(showtimeId:String)=SeatResult.Failure("unused")
 override suspend fun lock(showtimeId:String,seatIds:List<String>,version:Long,key:String)=SeatResult.Failure("unused")
 override suspend fun getLock(lockId:String)=SeatResult.Failure("unused")
 override suspend fun release(lockId:String)=SeatResult.Failure("unused")
}
private class FakePaymentRepo:OrderRepository{
 private val order=ServerOrder("o","PAYMENT_PROCESSING",100,"CNY","2026-01-01T00:10:00Z")
 override suspend fun quote(lockId:String)=SeatResult.Failure("unused")
 override suspend fun create(lockId:String,quoteVersion:Int,idempotencyKey:String)=SeatResult.Failure("unused")
 override suspend fun pay(orderId:String,method:String,idempotencyKey:String)=SeatResult.Success(PaymentAttempt("a",orderId,method,"PROCESSING",Money(100,"CNY"),"r","now"))
 override suspend fun getOrder(id:String)=SeatResult.Success(order)
 override suspend fun listOrders(): SeatResult<List<ServerOrder>> = SeatResult.Success(emptyList())
 override suspend fun ticket(id:String)=SeatResult.Failure("unused")
}

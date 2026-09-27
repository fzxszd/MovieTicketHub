package me.ibrahim.moviesapp.compose.presentation.order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.ibrahim.moviesapp.compose.domain.cinema.Money
import me.ibrahim.moviesapp.compose.domain.order.*
import me.ibrahim.moviesapp.compose.domain.seat.SeatResult
import org.junit.*
@OptIn(ExperimentalCoroutinesApi::class)
class OrderConfirmationViewModelTest{
 private val dispatcher=UnconfinedTestDispatcher()
 @Before fun setUp(){Dispatchers.setMain(dispatcher)}
 @After fun tearDown(){Dispatchers.resetMain()}
 @Test fun confirmation_uses_server_order()=runBlocking{val repo=FakeRepo();val vm=me.ibrahim.moviesapp.compose.presentation.order_confirmation.OrderConfirmationViewModel(repo);vm.load("lock");vm.confirm();Assert.assertEquals(1,repo.createCalls);Assert.assertEquals("PENDING_PAYMENT",vm.state.value.order?.status)}
}
private class FakeRepo:OrderRepository{
 var createCalls=0;private val money=Money(100,"CNY");private val quote=OrderQuote("lock",1,MovieSnapshot(1,"M"),PlaceSnapshot("c","C"),PlaceSnapshot("a","A"),ShowtimeSnapshot("s","2026-01-01T00:00:00Z","UTC"),emptyList(),money,money,money,"2026-01-01T00:10:00Z")
 override suspend fun quote(lockId:String)=SeatResult.Success(quote)
 override suspend fun create(lockId:String,quoteVersion:Int,idempotencyKey:String):SeatResult<ServerOrder>{createCalls++;return SeatResult.Success(ServerOrder("o","PENDING_PAYMENT",100,"CNY",quote.expiresAt))}
 override suspend fun pay(orderId:String,method:String,idempotencyKey:String)=SeatResult.Failure("unused")
 override suspend fun getOrder(id:String)=SeatResult.Failure("unused")
 override suspend fun listOrders(): SeatResult<List<ServerOrder>> = SeatResult.Success(emptyList())
 override suspend fun ticket(id:String)=SeatResult.Failure("unused")
}

package me.ibrahim.moviesapp.compose.data

import kotlinx.coroutines.runBlocking
import me.ibrahim.moviesapp.compose.data.dto.MoneyDto
import me.ibrahim.moviesapp.compose.data.network.*
import me.ibrahim.moviesapp.compose.data.repository.OrderRepositoryImpl
import me.ibrahim.moviesapp.compose.domain.seat.SeatResult
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderRepositoryTest {
    @Test fun quote_and_create_map_server_authority() = runBlocking {
        val api=FakeOrderApi();val repo=OrderRepositoryImpl(api)
        val quote=(repo.quote("lock") as SeatResult.Success).value
        assertEquals(4500,quote.total.amountMinor);assertEquals("CNY",quote.total.currency)
        val order=(repo.create("lock",quote.quoteVersion,"create-key-000000") as SeatResult.Success).value
        assertEquals("PENDING_PAYMENT",order.status);assertEquals(quote.total.amountMinor,order.totalMinor)
    }
}
private class FakeOrderApi:OrderRemoteApi {
 private val money=MoneyDto(4500,"CNY")
 override suspend fun quote(lockId:String)=SeatResult.Success(OrderQuoteDto(lockId,1,MovieSnapshotDto(1,"Movie"),SnapshotDto("c","Cinema"),SnapshotDto("a","Hall"),ShowtimeSnapshotDto("s","2026-09-25T02:00:00Z","Asia/Shanghai"),listOf(OrderItemDto("A-01","A","1","standard",money)),money,MoneyDto(0,"CNY"),money,"2026-09-25T02:10:00Z"))
 override suspend fun create(lockId:String,quoteVersion:Int,key:String)=SeatResult.Success(ServerOrderDto("order","PENDING_PAYMENT",money,"2026-09-25T02:10:00Z"))
 override suspend fun pay(orderId:String,method:String,key:String)=SeatResult.Success(PaymentAttemptDto("attempt",orderId,method,"PROCESSING",money,"ref","2026-09-25T02:00:00Z"))
 override suspend fun order(id:String)=SeatResult.Failure("UNUSED")
 override suspend fun orders()=SeatResult.Success(ServerOrdersDto(emptyList()))
 override suspend fun ticket(id:String)=SeatResult.Failure("UNUSED")
}

package me.ibrahim.moviesapp.compose.presentation.seat_selection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import me.ibrahim.moviesapp.compose.domain.cinema.Money
import me.ibrahim.moviesapp.compose.domain.order.*
import me.ibrahim.moviesapp.compose.domain.seat.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeatSelectionViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun selection_is_limited_to_six_and_seventh_seat_has_message() = runTest {
        val vm = SeatSelectionViewModel(FakeSeatRepository(), FakeOrderRepository())
        vm.load("show")
        (1..7).forEach { vm.onSeatClick("A-${it.toString().padStart(2, '0')}") }
        assertEquals(6, vm.uiState.value.selectedCount)
        assertEquals("MAX_SEATS", vm.uiState.value.error)
    }

    @Test fun stale_server_snapshot_removes_local_selection() = runTest {
        val repo = FakeSeatRepository(); val vm = SeatSelectionViewModel(repo, FakeOrderRepository())
        vm.load("show"); runCurrent(); vm.onSeatClick("A-01")
        repo.available = emptySet(); vm.load("show")
        assertTrue(vm.uiState.value.selectedIds.isEmpty())
    }

    @Test fun lock_and_continue_invoke_navigation_callbacks_with_server_ids() = runTest {
        val vm = SeatSelectionViewModel(FakeSeatRepository(), FakeOrderRepository())
        vm.load("show"); runCurrent(); vm.onSeatClick("A-01")
        var lockId: String? = null
        vm.lockSelected { lockId = it.id }; runCurrent()
        assertEquals("lock-1", lockId)
        var continuedLockId: String? = null
        vm.continueToPayment { continuedLockId = it }; runCurrent()
        assertEquals("lock-1", continuedLockId)
        vm.releaseActiveLock(); runCurrent()
    }
}

private class FakeOrderRepository : OrderRepository {
    override suspend fun quote(lockId: String): SeatResult<OrderQuote> = SeatResult.Success(OrderQuote(lockId,1,MovieSnapshot(1,"Movie"),PlaceSnapshot("c","Cinema"),PlaceSnapshot("a","Hall"),ShowtimeSnapshot("show","2026-09-25T03:00:00Z","UTC"),emptyList(),Money(100,"CNY"),Money(0,"CNY"),Money(100,"CNY"),"2026-09-25T03:10:00Z"))
    override suspend fun create(lockId: String, quoteVersion: Int, idempotencyKey: String): SeatResult<ServerOrder> = SeatResult.Success(ServerOrder("order-1","PENDING_PAYMENT",100,"CNY","2026-09-25T03:10:00Z"))
    override suspend fun pay(orderId: String, method: String, idempotencyKey: String): SeatResult<PaymentAttempt> = SeatResult.Failure("NOT_USED")
    override suspend fun getOrder(id: String): SeatResult<ServerOrder> = SeatResult.Failure("NOT_USED")
    override suspend fun listOrders(): SeatResult<List<ServerOrder>> = SeatResult.Success(emptyList())
    override suspend fun ticket(id: String): SeatResult<Ticket> = SeatResult.Failure("NOT_USED")
}

private class FakeSeatRepository : SeatRepository {
    var available: Set<String> = (1..7).map { "A-${it.toString().padStart(2, '0')}" }.toSet()
    override suspend fun snapshot(showtimeId: String): SeatResult<SeatLayoutSnapshot> = SeatResult.Success(
        SeatLayoutSnapshot(showtimeId, "aud", "Hall", "Screen", "2026-09-25T02:00:00Z", 1,
            listOf(SeatRow(0, "A", (1..7).map { n -> val id = "A-${n.toString().padStart(2, '0')}"; SeatPosition(id, 0, n, "A", n.toString(), SeatPositionType.SEAT, "standard", Money(4500, "CNY"), if (id in available) SeatInventoryStatus.AVAILABLE else SeatInventoryStatus.LOCKED_BY_OTHER, null) }))))
    override suspend fun lock(showtimeId: String, seatIds: List<String>, version: Long, key: String): SeatResult<SeatLock> = SeatResult.Success(SeatLock("lock-1",showtimeId,SeatLockStatus.ACTIVE,"2026-09-25T02:00:00Z","2026-09-25T02:00:00Z","2026-09-25T02:10:00Z",emptyList(),Money(100,"CNY"),null))
    override suspend fun getLock(lockId: String): SeatResult<SeatLock> = SeatResult.Failure("NOT_USED")
    override suspend fun release(lockId: String): SeatResult<SeatLock> = SeatResult.Failure("NOT_USED")
}

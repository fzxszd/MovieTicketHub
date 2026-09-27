package me.ibrahim.moviesapp.compose.presentation.settings

import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.ibrahim.moviesapp.compose.FakeAuthRepository
import me.ibrahim.moviesapp.compose.domain.MoviesRepository
import me.ibrahim.moviesapp.compose.domain.auth.AuthState
import me.ibrahim.moviesapp.compose.domain.auth.AuthUser
import me.ibrahim.moviesapp.compose.domain.order.OrderRepository
import me.ibrahim.moviesapp.compose.domain.order.ServerOrder
import me.ibrahim.moviesapp.compose.domain.order.TicketSummary
import me.ibrahim.moviesapp.compose.domain.seat.SeatResult
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun logoutIsIdempotentAndPublishesGuestState() {
        val auth = FakeAuthRepository().apply { setState(AuthState.Authenticated(FakeAuthRepository.USER)) }
        val movies = fakeMoviesRepository()
        val viewModel = SettingsViewModel(movies, auth, ProfileImageStore { null })
        var callbacks = 0
        viewModel.logout { callbacks++ }
        viewModel.logout { callbacks++ }
        assertEquals(1, auth.logoutCalls)
        assertEquals(AuthState.Guest, auth.state.value)
        assertEquals(2, callbacks)
    }

    @Test fun profileSwitchesWithoutLeakingPreviousAccount() {
        val auth = FakeAuthRepository().apply { setState(AuthState.Authenticated(FakeAuthRepository.USER)) }
        val viewModel = SettingsViewModel(fakeMoviesRepository(), auth, ProfileImageStore { null })
        assertEquals("alice@example.com", viewModel.userProfile.value.email)
        auth.setState(AuthState.Authenticated(AuthUser("2", "bob@example.com", "Bob")))
        assertEquals("bob@example.com", viewModel.userProfile.value.email)
        assertEquals("Bob", viewModel.userProfile.value.name)
    }

    @Test fun refundTicketCallsRemoteRepositoryAndRemovesTicketAfterSuccess() {
        val auth = FakeAuthRepository().apply { setState(AuthState.Authenticated(FakeAuthRepository.USER)) }
        val ticket = TicketSummary(
            id = "ticket-1", orderId = "order-1", status = "READY", credential = null,
            issuedAt = "2026-09-26T00:00:00Z", movieTitle = "测试电影", posterUrl = null,
            cinemaName = "测试影院", auditoriumName = "1号厅", startsAt = null,
            seats = listOf("A1"), totalMinor = 5000, currency = "CNY"
        )
        var refundedOrderId: String? = null
        val orderRepository = Proxy.newProxyInstance(
            OrderRepository::class.java.classLoader,
            arrayOf(OrderRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "listTickets" -> SeatResult.Success(listOf(ticket))
                "refund" -> {
                    refundedOrderId = args?.firstOrNull() as? String
                    SeatResult.Success(ServerOrder("order-1", "REFUNDED", 5000, "CNY", "2026-09-26T01:00:00Z"))
                }
                else -> SeatResult.Failure("UNEXPECTED_${method.name}")
            }
        } as OrderRepository
        val viewModel = SettingsViewModel(
            fakeMoviesRepository(), auth, ProfileImageStore { null }, orderRepository = orderRepository
        )

        var result: Boolean? = null
        viewModel.refundTicket(ticket) { result = it }

        assertEquals("order-1", refundedOrderId)
        assertEquals(true, result)
        assertTrue(viewModel.tickets.value.none { it.id == ticket.id })
    }
}

private fun fakeMoviesRepository(): MoviesRepository {
    return Proxy.newProxyInstance(
        MoviesRepository::class.java.classLoader,
        arrayOf(MoviesRepository::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "getAllOrders" -> flowOf(emptyList<Any>())
            "getRecommendedMovies", "getIsRecLoading", "getRecWeights" -> flowOf(emptyList<Any>())
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Float.TYPE -> 0f
                java.lang.Double.TYPE -> 0.0
                else -> kotlin.Unit
            }
        }
    } as MoviesRepository
}

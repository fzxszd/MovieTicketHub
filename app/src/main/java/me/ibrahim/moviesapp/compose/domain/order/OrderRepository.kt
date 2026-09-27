package me.ibrahim.moviesapp.compose.domain.order

import me.ibrahim.moviesapp.compose.domain.cinema.Money
import me.ibrahim.moviesapp.compose.domain.seat.SeatResult

data class MovieSnapshot(val id:Int,val title:String,val posterUrl:String?=null)
data class PlaceSnapshot(val id:String,val name:String)
data class ShowtimeSnapshot(val id:String,val startsAt:String,val timeZone:String)
data class OrderItem(val seatId:String,val rowLabel:String,val seatLabel:String,val priceZoneId:String?,val unitPrice:Money)
data class OrderQuote(val lockId:String,val quoteVersion:Int,val movie:MovieSnapshot,val cinema:PlaceSnapshot,val auditorium:PlaceSnapshot,val showtime:ShowtimeSnapshot,val items:List<OrderItem>,val subtotal:Money,val fees:Money,val total:Money,val expiresAt:String)
enum class OrderStatus { PENDING_PAYMENT, PAYMENT_PROCESSING, PAID, PAYMENT_FAILED, CANCELLED, EXPIRED, REVIEW_REQUIRED }
data class PaymentAttempt(val id:String,val orderId:String,val method:String,val status:String,val amount:Money,val transactionRef:String?=null,val createdAt:String)
data class PaymentConfirmation(val orderId:String,val paymentAttemptId:String,val orderStatus:String,val paymentStatus:String,val ticketReady:Boolean,val ticketId:String?,val serverTimestamp:String)
data class Ticket(val id:String,val orderId:String,val status:String,val credential:String?,val issuedAt:String)
data class TicketSummary(val id:String,val orderId:String,val status:String,val credential:String?,val issuedAt:String,val movieTitle:String?,val posterUrl:String?,val cinemaName:String?,val auditoriumName:String?,val startsAt:String?,val seats:List<String>,val totalMinor:Long,val currency:String)
data class ServerOrder(val id:String,val status:String,val totalMinor:Long,val currency:String,val paymentDeadline:String,val createdAt:String="",val quoteVersion:Int=1,val movie:MovieSnapshot?=null,val cinema:PlaceSnapshot?=null,val auditorium:PlaceSnapshot?=null,val showtime:ShowtimeSnapshot?=null,val items:List<OrderItem> = emptyList(),val paidAt:String?=null,val allowedActions:List<String> = emptyList(),val ticket:Ticket?=null)
interface OrderRepository {
    suspend fun quote(lockId:String): SeatResult<OrderQuote>
    suspend fun create(lockId:String,quoteVersion:Int,idempotencyKey:String): SeatResult<ServerOrder>
    suspend fun pay(orderId:String,method:String,idempotencyKey:String): SeatResult<PaymentAttempt>
    suspend fun confirmPayment(orderId:String,attemptId:String): SeatResult<PaymentConfirmation> = SeatResult.Failure("UNSUPPORTED")
    suspend fun getOrder(id:String): SeatResult<ServerOrder>
    suspend fun listOrders(): SeatResult<List<ServerOrder>>
    suspend fun ticket(id:String): SeatResult<Ticket>
    suspend fun listTickets(): SeatResult<List<TicketSummary>> = SeatResult.Success(emptyList())
    suspend fun refund(orderId: String): SeatResult<ServerOrder> = SeatResult.Failure("UNSUPPORTED")
}

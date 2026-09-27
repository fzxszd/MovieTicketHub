package me.ibrahim.moviesapp.compose.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import me.ibrahim.moviesapp.compose.data.dto.MoneyDto
import me.ibrahim.moviesapp.compose.domain.auth.AuthRepository
import me.ibrahim.moviesapp.compose.domain.seat.SeatResult

@Serializable data class SnapshotDto(val id:String="",val name:String="")
@Serializable data class MovieSnapshotDto(val id:Int,val title:String,val posterUrl:String?=null)
@Serializable data class ShowtimeSnapshotDto(val id:String,val startsAt:String,val timeZone:String)
@Serializable data class OrderItemDto(val seatId:String,val rowLabel:String,val seatLabel:String,val priceZoneId:String?=null,val unitPrice:MoneyDto)
@Serializable data class OrderQuoteDto(val lockId:String,val quoteVersion:Int,val movie:MovieSnapshotDto,val cinema:SnapshotDto,val auditorium:SnapshotDto,val showtime:ShowtimeSnapshotDto,val items:List<OrderItemDto>,val subtotal:MoneyDto,val fees:MoneyDto,val total:MoneyDto,val expiresAt:String)
@Serializable data class ServerOrderDto(val id:String,val status:String,val total:MoneyDto,val paymentDeadline:String,val createdAt:String="",val quoteVersion:Int=1,val movie:MovieSnapshotDto?=null,val cinema:SnapshotDto?=null,val auditorium:SnapshotDto?=null,val showtime:ShowtimeSnapshotDto?=null,val items:List<OrderItemDto> = emptyList(),val paidAt:String?=null,val allowedActions:List<String> = emptyList(),val ticket:TicketDto?=null)
@Serializable data class ServerOrdersDto(val orders:List<ServerOrderDto>,val nextCursor:String?=null)
@Serializable data class PaymentAttemptDto(val id:String,val orderId:String,val method:String,val status:String,val amount:MoneyDto,val transactionRef:String?=null,val createdAt:String)
@Serializable data class PaymentConfirmationDto(val orderId:String,val paymentAttemptId:String,val orderStatus:String,val paymentStatus:String,val ticketReady:Boolean,val ticketId:String?=null,val serverTimestamp:String)
@Serializable data class TicketDto(val id:String,val orderId:String,val status:String,val credential:String?=null,val issuedAt:String,val ticketId:String?=null,val movieId:Int?=null,val movieTitle:String?=null,val posterUrl:String?=null,val cinemaName:String?=null,val auditoriumName:String?=null,val startsAt:String?=null,val timeZone:String?=null,val seats:List<String> = emptyList(),val total:MoneyDto?=null,val paidAt:String?=null)
@Serializable data class TicketsDto(val tickets:List<TicketDto> = emptyList())
@Serializable data class PaymentRequestDto(val method:String)
@Serializable data class CreateOrderRequestDto(val lockId:String,val acceptedQuoteVersion:Int)
interface OrderRemoteApi {
    suspend fun quote(lockId:String):SeatResult<OrderQuoteDto>
    suspend fun create(lockId:String,quoteVersion:Int,key:String):SeatResult<ServerOrderDto>
    suspend fun pay(orderId:String,method:String,key:String):SeatResult<PaymentAttemptDto>
    suspend fun confirmPayment(orderId:String,attemptId:String):SeatResult<PaymentConfirmationDto> = SeatResult.Failure("UNSUPPORTED")
    suspend fun order(id:String):SeatResult<ServerOrderDto>
    suspend fun orders():SeatResult<ServerOrdersDto>
    suspend fun ticket(id:String):SeatResult<TicketDto>
    suspend fun tickets():SeatResult<TicketsDto> = SeatResult.Success(TicketsDto())
    suspend fun refund(orderId:String):SeatResult<ServerOrderDto> = SeatResult.Failure("UNSUPPORTED")
}
class OrderRemoteApiImpl(private val client:HttpClient,private val auth:AuthRepository):OrderRemoteApi {
    private suspend inline fun <reified T> call(crossinline block:suspend()->HttpResponse):SeatResult<T> = try { val r=block(); if(r.status.isSuccess()) SeatResult.Success(r.body()) else SeatResult.Failure("HTTP_${r.status.value}") } catch(_:Exception){SeatResult.Failure("NETWORK")}
    private fun io.ktor.client.request.HttpRequestBuilder.bearer(){auth.currentToken()?.let{bearerAuth(it)}}
    override suspend fun quote(lockId:String)=call<OrderQuoteDto>{client.get("${RemoteApiEndpoints.AUTH_BASE_URL}/v1/seat-locks/$lockId/order-quote"){bearer()}}
    override suspend fun create(lockId:String,quoteVersion:Int,key:String)=call<ServerOrderDto>{client.post("${RemoteApiEndpoints.AUTH_BASE_URL}/v1/orders"){bearer();header("Idempotency-Key",key);contentType(ContentType.Application.Json);setBody(CreateOrderRequestDto(lockId,quoteVersion))}}
    override suspend fun pay(orderId:String,method:String,key:String)=call<PaymentAttemptDto>{client.post("${RemoteApiEndpoints.AUTH_BASE_URL}/v1/orders/$orderId/payment-attempts"){bearer();header("Idempotency-Key",key);contentType(ContentType.Application.Json);setBody(PaymentRequestDto(method))}}
    override suspend fun confirmPayment(orderId:String,attemptId:String)=call<PaymentConfirmationDto>{client.post("${RemoteApiEndpoints.AUTH_BASE_URL}/v1/orders/$orderId/payment-confirmations"){bearer();contentType(ContentType.Application.Json);setBody(mapOf("paymentAttemptId" to attemptId))}}
    override suspend fun order(id:String)=call<ServerOrderDto>{client.get("${RemoteApiEndpoints.AUTH_BASE_URL}/v1/orders/$id"){bearer()}}
    override suspend fun orders()=call<ServerOrdersDto>{client.get("${RemoteApiEndpoints.AUTH_BASE_URL}/v1/orders"){bearer()}}
    override suspend fun ticket(id:String)=call<TicketDto>{client.get("${RemoteApiEndpoints.AUTH_BASE_URL}/v1/orders/$id/ticket"){bearer()}}
    override suspend fun tickets()=call<TicketsDto>{client.get("${RemoteApiEndpoints.AUTH_BASE_URL}/v1/tickets"){bearer()}}
    override suspend fun refund(orderId:String)=call<ServerOrderDto>{client.post("${RemoteApiEndpoints.AUTH_BASE_URL}/v1/orders/$orderId/refund"){bearer()}}
}

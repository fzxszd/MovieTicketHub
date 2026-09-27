package me.ibrahim.moviesapp.compose.data.repository

import me.ibrahim.moviesapp.compose.data.network.*
import me.ibrahim.moviesapp.compose.domain.cinema.Money
import me.ibrahim.moviesapp.compose.domain.order.*
import me.ibrahim.moviesapp.compose.domain.seat.SeatResult
import java.util.UUID

private fun MoneyDtoToDomain(value:me.ibrahim.moviesapp.compose.data.dto.MoneyDto)=Money(value.amountMinor,value.currency)
private fun OrderItemDto.toDomain()=OrderItem(seatId,rowLabel,seatLabel,priceZoneId,MoneyDtoToDomain(unitPrice))
private fun OrderQuoteDto.toDomain()=OrderQuote(lockId,quoteVersion,MovieSnapshot(movie.id,movie.title,movie.posterUrl),PlaceSnapshot(cinema.id,cinema.name),PlaceSnapshot(auditorium.id,auditorium.name),ShowtimeSnapshot(showtime.id,showtime.startsAt,showtime.timeZone),items.map{it.toDomain()},MoneyDtoToDomain(subtotal),MoneyDtoToDomain(fees),MoneyDtoToDomain(total),expiresAt)
private fun TicketDto.toDomain()=Ticket(id,orderId,status,credential,issuedAt)
private fun ServerOrderDto.toDomain()=ServerOrder(id,status,total.amountMinor,total.currency,paymentDeadline,createdAt,quoteVersion,movie?.let{MovieSnapshot(it.id,it.title,it.posterUrl)},cinema?.let{PlaceSnapshot(it.id,it.name)},auditorium?.let{PlaceSnapshot(it.id,it.name)},showtime?.let{ShowtimeSnapshot(it.id,it.startsAt,it.timeZone)},items.map{it.toDomain()},paidAt,allowedActions,ticket?.toDomain())

class OrderRepositoryImpl(private val api:OrderRemoteApi):OrderRepository {
    override suspend fun quote(lockId:String)=when(val r=api.quote(lockId)){is SeatResult.Success->SeatResult.Success(r.value.toDomain());is SeatResult.Failure->r}
    override suspend fun create(lockId:String,quoteVersion:Int,idempotencyKey:String)=when(val r=api.create(lockId,quoteVersion,idempotencyKey)){is SeatResult.Success->SeatResult.Success(r.value.toDomain());is SeatResult.Failure->r}
    override suspend fun pay(orderId:String,method:String,idempotencyKey:String)=when(val r=api.pay(orderId,method,idempotencyKey)){is SeatResult.Success->SeatResult.Success(PaymentAttempt(r.value.id,r.value.orderId,r.value.method,r.value.status,MoneyDtoToDomain(r.value.amount),r.value.transactionRef,r.value.createdAt));is SeatResult.Failure->r}
    override suspend fun confirmPayment(orderId:String,attemptId:String)=when(val r=api.confirmPayment(orderId,attemptId)){is SeatResult.Success->SeatResult.Success(PaymentConfirmation(r.value.orderId,r.value.paymentAttemptId,r.value.orderStatus,r.value.paymentStatus,r.value.ticketReady,r.value.ticketId,r.value.serverTimestamp));is SeatResult.Failure->r}
    override suspend fun getOrder(id:String)=when(val r=api.order(id)){is SeatResult.Success->SeatResult.Success(r.value.toDomain());is SeatResult.Failure->r}
    override suspend fun listOrders()=when(val r=api.orders()){is SeatResult.Success->SeatResult.Success(r.value.orders.map{it.toDomain()});is SeatResult.Failure->r}
    override suspend fun ticket(id:String)=when(val r=api.ticket(id)){is SeatResult.Success->SeatResult.Success(r.value.toDomain());is SeatResult.Failure->r}
    override suspend fun listTickets()=when(val r=api.tickets()){is SeatResult.Success->SeatResult.Success(r.value.tickets.map{TicketSummary(it.id,it.orderId,it.status,it.credential,it.issuedAt,it.movieTitle,it.posterUrl,it.cinemaName,it.auditoriumName,it.startsAt,it.seats,it.total?.amountMinor ?: 0L,it.total?.currency ?: "CNY")});is SeatResult.Failure->r}
    override suspend fun refund(orderId:String)=when(val r=api.refund(orderId)){is SeatResult.Success->SeatResult.Success(r.value.toDomain());is SeatResult.Failure->r}
}

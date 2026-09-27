package me.ibrahim.moviesapp.compose.domain.cinema

data class Money(val amountMinor: Long, val currency: String) {
    init { require(amountMinor >= 0); require(currency.matches(Regex("[A-Z]{3}"))) }
    fun format(): String = if (currency == "CNY") "¥%.2f".format(amountMinor / 100.0) else "$currency %.2f".format(amountMinor / 100.0)
}

package me.ibrahim.moviesapp.compose.presentation.payment

import android.content.Context
import me.ibrahim.moviesapp.compose.BuildConfig

data class PaymentAccount(val id: String, val displayName: String, val balanceMinor: Long, val enabled: Boolean = true)

object SimulatedPaymentConfig {
    const val ALIPAY_ID = "ALIPAY_SIMULATED"
    const val WECHAT_ID = "WECHAT_SIMULATED"
    const val ALIPAY_INITIAL_BALANCE_MINOR = 1_000_000L // ¥10,000.00
    const val WECHAT_INITIAL_BALANCE_MINOR = 1_000_000L // ¥10,000.00
    const val ALIPAY_PREF_KEY = "alipay_balance_minor"
    const val WECHAT_PREF_KEY = "wechat_balance_minor"
}

class PaymentAccountStore(context: Context) {
    private val preferences = context.getSharedPreferences("simulated_payment_accounts", Context.MODE_PRIVATE)

    init {
        // This is a one-time reset for the local debug payment simulator.
        // Balances are client-side test data and are not stored in backend/app.py.
        if (BuildConfig.DEBUG && preferences.getInt("test_balance_seed_version", 0) < 1) {
            preferences.edit()
                .putLong(SimulatedPaymentConfig.ALIPAY_PREF_KEY, SimulatedPaymentConfig.ALIPAY_INITIAL_BALANCE_MINOR)
                .putLong(SimulatedPaymentConfig.WECHAT_PREF_KEY, SimulatedPaymentConfig.WECHAT_INITIAL_BALANCE_MINOR)
                .putInt("test_balance_seed_version", 1)
                .apply()
        }
    }

    private fun accountBalanceKey(id: String): String? = when (id) {
        SimulatedPaymentConfig.ALIPAY_ID -> SimulatedPaymentConfig.ALIPAY_PREF_KEY
        SimulatedPaymentConfig.WECHAT_ID -> SimulatedPaymentConfig.WECHAT_PREF_KEY
        else -> null
    }

    private fun paymentAccountKey(orderId: String) = "payment_account_$orderId"
    private fun refundedKey(orderId: String) = "refunded_$orderId"

    fun accounts(): List<PaymentAccount> = listOf(
        PaymentAccount(SimulatedPaymentConfig.ALIPAY_ID, "支付宝模拟", preferences.getLong(SimulatedPaymentConfig.ALIPAY_PREF_KEY, SimulatedPaymentConfig.ALIPAY_INITIAL_BALANCE_MINOR)),
        PaymentAccount(SimulatedPaymentConfig.WECHAT_ID, "微信模拟", preferences.getLong(SimulatedPaymentConfig.WECHAT_PREF_KEY, SimulatedPaymentConfig.WECHAT_INITIAL_BALANCE_MINOR))
    )
    fun debit(id: String, amountMinor: Long, debitKey: String? = null, orderId: String? = null): Boolean {
        if (debitKey != null && preferences.getBoolean("debit_$debitKey", false)) return true
        val account = accounts().firstOrNull { it.id == id } ?: return false
        if (!account.enabled || amountMinor < 0 || account.balanceMinor < amountMinor) return false
        val key = accountBalanceKey(id) ?: return false
        val editor = preferences.edit().putLong(key, account.balanceMinor - amountMinor)
        if (debitKey != null) editor.putBoolean("debit_$debitKey", true)
        if (orderId != null) editor.putString(paymentAccountKey(orderId), id)
        editor.apply()
        return true
    }

    /** Restores the paid amount to the same simulated account after a refund. */
    fun creditForRefund(orderId: String, amountMinor: Long): Boolean {
        if (amountMinor < 0 || preferences.getBoolean(refundedKey(orderId), false)) return false
        val accountId = preferences.getString(paymentAccountKey(orderId), null) ?: return false
        val balanceKey = accountBalanceKey(accountId) ?: return false
        val currentBalance = preferences.getLong(balanceKey, 0L)
        preferences.edit()
            .putLong(balanceKey, currentBalance + amountMinor)
            .putBoolean(refundedKey(orderId), true)
            .remove(paymentAccountKey(orderId))
            .apply()
        return true
    }
}

package me.ibrahim.moviesapp.compose.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StoredSession(val token: String, val expiresAtMillis: Long, val userIdHint: String?)

interface SessionStore {
    fun save(token: String, expiresAt: String, userIdHint: String?)
    fun read(): StoredSession?
    fun clear()
    fun moveCurrentToPendingRevocation()
    fun readPendingRevocation(): String?
    fun clearPendingRevocation()
}

class KeystoreSessionStore(context: Context) : SessionStore {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    override fun save(token: String, expiresAt: String, userIdHint: String?) {
        val encrypted = encrypt(token)
        preferences.edit()
            .putString(TOKEN, encrypted.first)
            .putString(IV, encrypted.second)
            .putLong(EXPIRES, parseInstant(expiresAt))
            .putString(USER_HINT, userIdHint)
            .apply()
    }

    @Synchronized
    override fun read(): StoredSession? {
        val token = decrypt(preferences.getString(TOKEN, null), preferences.getString(IV, null))
            ?: return clearAndNull()
        val expires = preferences.getLong(EXPIRES, 0L)
        if (expires <= System.currentTimeMillis()) return clearAndNull()
        return StoredSession(token, expires, preferences.getString(USER_HINT, null))
    }

    @Synchronized
    override fun clear() {
        preferences.edit().remove(TOKEN).remove(IV).remove(EXPIRES).remove(USER_HINT).apply()
    }

    @Synchronized
    override fun moveCurrentToPendingRevocation() {
        val session = read() ?: return
        val encrypted = encrypt(session.token)
        preferences.edit().putString(PENDING_TOKEN, encrypted.first)
            .putString(PENDING_IV, encrypted.second).apply()
        clear()
    }

    override fun readPendingRevocation(): String? = decrypt(
        preferences.getString(PENDING_TOKEN, null), preferences.getString(PENDING_IV, null)
    )

    override fun clearPendingRevocation() {
        preferences.edit().remove(PENDING_TOKEN).remove(PENDING_IV).apply()
    }

    private fun clearAndNull(): StoredSession? { clear(); return null }

    private fun encrypt(value: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP) to
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun decrypt(ciphertext: String?, iv: String?): String? {
        if (ciphertext == null || iv == null) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)))
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    private fun parseInstant(value: String): Long {
        val normalized = value.replace(Regex("(\\.\\d{3})\\d+"), "$1")
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(normalized)?.time }.getOrNull()
        } ?: 0L
    }

    private companion object {
        const val PREFS = "secure_auth_session"
        const val KEY_ALIAS = "movies_auth_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TOKEN = "token_ciphertext"
        const val IV = "token_iv"
        const val EXPIRES = "expires_at"
        const val USER_HINT = "user_id_hint"
        const val PENDING_TOKEN = "pending_ciphertext"
        const val PENDING_IV = "pending_iv"
    }
}

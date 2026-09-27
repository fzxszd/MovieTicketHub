package me.ibrahim.moviesapp.compose.data.auth

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.*
import org.junit.Test

class SessionStoreTest {
    @Test fun aesGcmRoundTripAndTamperDetection() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val encryptor = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val iv = encryptor.iv
        val encrypted = encryptor.doFinal("opaque-token".toByteArray())
        val decryptor = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
        assertEquals("opaque-token", String(decryptor.doFinal(encrypted)))
        encrypted[0] = (encrypted[0].toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            }.doFinal(encrypted)
        }
    }

    @Test fun logoutAndExpiryNeverRestoreActiveToken() {
        val store = ContractStore(StoredSession("token", 1L, "user"))
        assertNull(store.read())
        store.session = StoredSession("token", Long.MAX_VALUE, "user")
        store.moveCurrentToPendingRevocation()
        assertNull(store.read())
        assertEquals("token", store.readPendingRevocation())
        store.clearPendingRevocation()
        assertNull(store.readPendingRevocation())
    }
}

private class ContractStore(var session: StoredSession?) : SessionStore {
    private var pending: String? = null
    override fun save(token: String, expiresAt: String, userIdHint: String?) = Unit
    override fun read(): StoredSession? = session?.takeIf { it.expiresAtMillis > System.currentTimeMillis() }.also {
        if (it == null) session = null
    }
    override fun clear() { session = null }
    override fun moveCurrentToPendingRevocation() { pending = read()?.token; clear() }
    override fun readPendingRevocation() = pending
    override fun clearPendingRevocation() { pending = null }
}

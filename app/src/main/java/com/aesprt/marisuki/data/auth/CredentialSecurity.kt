package com.aesprt.marisuki.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aesprt.marisuki.BuildConfig
import com.aesprt.marisuki.domain.auth.CredentialVerifier
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.first

private val Context.authStateStore by preferencesDataStore(name = if (BuildConfig.DEBUG) "marisuki_auth_state_debug" else "marisuki_auth_state")

class CredentialSecurity {
    fun createVerifier(secret: String): CredentialVerifier {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val verifier = derive(secret, salt)
        return CredentialVerifier(encoder.encodeToString(salt), encoder.encodeToString(verifier))
    }

    fun verify(secret: String, saltBase64: String, verifierBase64: String): Boolean = runCatching {
        val salt = decoder.decode(saltBase64)
        val expected = decoder.decode(verifierBase64)
        salt.size == SALT_BYTES && expected.size == HASH_BYTES &&
            MessageDigest.isEqual(expected, derive(secret, salt))
    }.getOrDefault(false)

    private fun derive(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, PBKDF_ITERATIONS, HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val SALT_BYTES = 16
        private const val HASH_BYTES = 32
        private const val PBKDF_ITERATIONS = 210_000
        private val random = SecureRandom()
        private val encoder = Base64.getEncoder()
        private val decoder = Base64.getDecoder()

        fun validOwnerPassword(value: String) = value.length in 8..128 && value.any(Char::isLetter) && value.any(Char::isDigit)
        fun validCashierPin(value: String) = value.matches(Regex("\\d{6}"))
        fun validOwnerApprovalPin(value: String) = value.matches(Regex("\\d{6}"))
    }
}

data class LocalSession(
    val sessionId: String,
    val accountId: String,
    val issuedAtMillis: Long,
    val lastActivityAtMillis: Long,
    val recentAuthenticationAtMillis: Long,
)

/** Stores only an encrypted opaque session marker; credentials are never persisted here. */
class EncryptedSessionStore(
    private val context: Context,
    private val stateStore: DataStore<Preferences> = context.authStateStore,
) {
    suspend fun save(session: LocalSession) {
        val cleartext = listOf(
            session.sessionId,
            session.accountId,
            session.issuedAtMillis.toString(),
            session.lastActivityAtMillis.toString(),
            session.recentAuthenticationAtMillis.toString(),
        ).joinToString("|").toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(cleartext)
        val packed = cipher.iv + encrypted
        stateStore.edit { it[SESSION_KEY] = Base64.getEncoder().encodeToString(packed) }
        cleartext.fill(0)
    }

    suspend fun read(): LocalSession? {
        val encoded = stateStore.data.first()[SESSION_KEY] ?: return null
        return runCatching {
            val packed = Base64.getDecoder().decode(encoded)
            if (packed.size <= IV_BYTES + TAG_BYTES) return@runCatching null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, packed.copyOfRange(0, IV_BYTES)))
            val cleartext = cipher.doFinal(packed.copyOfRange(IV_BYTES, packed.size))
            try {
                val fields = cleartext.toString(Charsets.UTF_8).split('|')
                if (fields.size != 5) return@runCatching null
                LocalSession(fields[0], fields[1], fields[2].toLong(), fields[3].toLong(), fields[4].toLong())
            } finally {
                cleartext.fill(0)
            }
        }.getOrElse {
            clear()
            null
        }
    }

    suspend fun clear() {
        stateStore.edit { it.remove(SESSION_KEY) }
    }

    suspend fun invalidateAccount(accountId: String) {
        val session = read() ?: return
        if (session.accountId == accountId) clear()
    }

    private fun getOrCreateKey(): SecretKey = synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private val KEY_ALIAS = if (BuildConfig.DEBUG) "marisuki_session_key_debug_v1" else "marisuki_session_key_v1"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / 8
        private val SESSION_KEY = stringPreferencesKey("encrypted_session")
        private val KEY_LOCK = Any()
    }
}

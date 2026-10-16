package com.aesprt.marisuki.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aesprt.marisuki.BuildConfig
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mariSukiSettings by preferencesDataStore(name = if (BuildConfig.DEBUG) "marisuki_settings_debug" else "marisuki_settings")

class AppPreferences(
    context: Context,
    private val settings: DataStore<Preferences> = context.mariSukiSettings,
) {
    val language: Flow<String> = settings.data.map { values ->
        values[LANGUAGE_KEY] ?: SYSTEM_LANGUAGE
    }
    val ownerPinConfigured: Flow<Boolean> = settings.data.map { values ->
        !values[OWNER_PIN_KEY].isNullOrBlank()
    }

    suspend fun setLanguage(language: String) {
        require(language in SUPPORTED_LANGUAGES)
        settings.edit { values -> values[LANGUAGE_KEY] = language }
    }

    suspend fun clearLegacyOwnerPin() {
        settings.edit { values -> values.remove(OWNER_PIN_KEY) }
    }

    suspend fun verifyOwnerPin(pin: String): Boolean {
        val stored = settings.data.map { it[OWNER_PIN_KEY] }.first() ?: return false
        val parts = stored.split(':')
        if (parts.size != 3 || parts[0] != "1" || !PIN_PATTERN.matches(pin)) return false
        return runCatching {
            val salt = Base64.getDecoder().decode(parts[1])
            val expected = Base64.getDecoder().decode(parts[2])
            MessageDigest.isEqual(expected, derivePinHash(pin, salt))
        }.getOrDefault(false)
    }

    companion object {
        const val SYSTEM_LANGUAGE = "system"
        val SUPPORTED_LANGUAGES = setOf(SYSTEM_LANGUAGE, "en", "fil")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val OWNER_PIN_KEY = stringPreferencesKey("owner_pin_hash")
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 120_000
        private const val KEY_BITS = 256
        private val PIN_PATTERN = Regex("\\d{4,8}")

        private fun derivePinHash(pin: String, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
            return try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }
    }
}

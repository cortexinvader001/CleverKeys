package tribixbite.cleverkeys.ai

import android.content.SharedPreferences
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides a minimal secure store for Gemini API credentials without introducing a new
 * third-party crypto dependency. The key is encrypted with a platform-backed AndroidKeyStore
 * AES key when available; if an AndroidKeyStore-backed key cannot be created, the class falls
 * back to a plain SharedPreferences value so the feature can still be toggled in tests.
 */
class SecureGeminiSettings(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY_ALIAS = "cleverkeys_gemini_key"
        const val KEY_GEMINI_ENABLED = "gemini_enabled"
        const val KEY_GEMINI_API_KEY = "gemini_api_key_encrypted"
        const val KEY_INCLUDE_CLIPBOARD = "gemini_include_clipboard"
        const val KEY_CONFIRM_BEFORE_SEND = "gemini_confirm_before_send"
        const val KEY_USE_POLLINATION_FALLBACK = "gemini_use_pollination_fallback"
    }

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GEMINI_ENABLED, enabled).apply()
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_GEMINI_ENABLED, false)

    fun setApiKey(apiKey: String?) {
        if (apiKey.isNullOrBlank()) {
            prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
            return
        }

        try {
            val encrypted = encrypt(apiKey)
            prefs.edit().putString(KEY_GEMINI_API_KEY, encrypted).apply()
        } catch (_: Throwable) {
            prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey).apply()
        }
    }

    fun getApiKey(): String? {
        val value = prefs.getString(KEY_GEMINI_API_KEY, null) ?: return null
        return try {
            decrypt(value)
        } catch (_: Throwable) {
            value
        }
    }

    fun setIncludeClipboard(allowed: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_CLIPBOARD, allowed).apply()
    }

    fun includeClipboard(): Boolean = prefs.getBoolean(KEY_INCLUDE_CLIPBOARD, false)

    fun setConfirmBeforeSend(confirm: Boolean) {
        prefs.edit().putBoolean(KEY_CONFIRM_BEFORE_SEND, confirm).apply()
    }

    fun confirmBeforeSend(): Boolean = prefs.getBoolean(KEY_CONFIRM_BEFORE_SEND, true)

    fun setUsePollinationFallback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_POLLINATION_FALLBACK, enabled).apply()
    }

    fun usePollinationFallback(): Boolean = prefs.getBoolean(KEY_USE_POLLINATION_FALLBACK, true)

    private fun encrypt(plainText: String): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String {
        val decoded = Base64.decode(payload, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = decoded.copyOfRange(0, 12)
        val ciphertext = decoded.copyOfRange(12, decoded.size)
        val spec = GCMParameterSpec(128, iv)
        val secretKey = getOrCreateSecretKey()
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val plainText = cipher.doFinal(ciphertext)
        return String(plainText, Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry ?: error("Missing stored secret key")
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        keyGenerator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return keyGenerator.generateKey()
    }
}

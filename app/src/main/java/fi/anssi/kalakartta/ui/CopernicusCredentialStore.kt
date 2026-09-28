package fi.anssi.kalakartta.ui

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CopernicusCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun saveClientSecret(secret: String) {
        if (secret.isEmpty()) {
            clearClientSecret()
            return
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(secret.toByteArray(StandardCharsets.UTF_8))
        val storedValue = cipher.iv + ciphertext
        preferences.edit().putString(ENCRYPTED_SECRET, Base64.encodeToString(storedValue, Base64.NO_WRAP)).apply()
    }

    fun getClientSecret(): String? {
        val encoded = preferences.getString(ENCRYPTED_SECRET, null) ?: return null
        return try {
            val storedValue = Base64.decode(encoded, Base64.NO_WRAP)
            if (storedValue.size <= GCM_IV_LENGTH) {
                preferences.edit().remove(ENCRYPTED_SECRET).apply()
                return null
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, storedValue.copyOfRange(0, GCM_IV_LENGTH))
            )
            String(cipher.doFinal(storedValue.copyOfRange(GCM_IV_LENGTH, storedValue.size)), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            preferences.edit().remove(ENCRYPTED_SECRET).apply()
            null
        }
    }

    fun hasClientSecret(): Boolean = !getClientSecret().isNullOrBlank()

    fun clearClientSecret() {
        preferences.edit().remove(ENCRYPTED_SECRET).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "copernicus_credentials"
        const val ENCRYPTED_SECRET = "encrypted_client_secret"
        const val KEY_ALIAS = "kalakartta_copernicus_client_secret"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
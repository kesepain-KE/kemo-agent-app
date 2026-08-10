package com.kesepain.kemoapp.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(private val context: Context) {
    suspend fun put(accountId: String, name: String, value: String) {
        context.kemoDataStore.edit { it[stringPreferencesKey(key(accountId, name))] = encrypt(value) }
    }

    suspend fun get(accountId: String, name: String): String {
        val encoded = context.kemoDataStore.data.map { it[stringPreferencesKey(key(accountId, name))].orEmpty() }.first()
        return if (encoded.isBlank()) "" else runCatching { decrypt(encoded) }.getOrDefault("")
    }

    suspend fun clear(accountId: String, name: String) {
        context.kemoDataStore.edit { it.remove(stringPreferencesKey(key(accountId, name))) }
    }

    suspend fun update(accountId: String, values: Map<String, String>, remove: Set<String> = emptySet()) {
        val encrypted = values.mapValues { (_, value) -> encrypt(value) }
        context.kemoDataStore.edit { preferences ->
            remove.forEach { name -> preferences.remove(stringPreferencesKey(key(accountId, name))) }
            encrypted.forEach { (name, value) -> preferences[stringPreferencesKey(key(accountId, name))] = value }
        }
    }

    suspend fun clear(accountId: String, names: Collection<String>) {
        context.kemoDataStore.edit { preferences ->
            names.forEach { name -> preferences.remove(stringPreferencesKey(key(accountId, name))) }
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun key(accountId: String, name: String) = "secure_${accountId}_${name}"

    companion object {
        private const val ALIAS = "kemo_app_secure_store"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DEVICE_TOKEN = "device_token"
        const val USER_PASSWORD = "user_password"
        const val SESSION_TOKEN = "session_token"
        const val SESSION_EXPIRES_AT = "session_expires_at"
        const val REMEMBER_CREDENTIALS = "remember_credentials"
        const val CREDENTIALS_EXPIRE_AT = "credentials_expire_at"
        const val APP_PASSWORD_HASH = "app_password_hash"
    }
}

package com.lvupme.app.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeystoreCrypto {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "lvupme_gemini_api_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    @Synchronized
    private fun getOrCreateKey(forceRecreate: Boolean = false): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (forceRecreate) {
            runCatching { keyStore.deleteEntry(KEY_ALIAS) }
        } else {
            val existing = runCatching {
                keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            }.getOrNull()
            if (existing != null) return existing.secretKey
            // Corrupted entry exists -> remove before creating
            runCatching {
                if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    @Synchronized
    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        return try {
            encryptWithKey(plainText, getOrCreateKey(forceRecreate = false))
        } catch (_: Exception) {
            // Recover once if Keystore key was invalidated or corrupted
            runCatching {
                encryptWithKey(plainText, getOrCreateKey(forceRecreate = true))
            }.getOrDefault("")
        }
    }

    private fun encryptWithKey(plainText: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(1 + iv.size + cipherText.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(cipherText, 0, combined, 1 + iv.size, cipherText.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    @Synchronized
    fun decrypt(encoded: String): String {
        if (encoded.isBlank()) return ""
        return runCatching {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < 2) return ""
            val ivLength = combined[0].toInt() and 0xFF
            if (ivLength <= 0 || 1 + ivLength >= combined.size) return ""
            val iv = combined.copyOfRange(1, 1 + ivLength)
            val cipherText = combined.copyOfRange(1 + ivLength, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(forceRecreate = false), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrDefault("")
    }
}

package com.gallerybridge.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The opt-in Mail app password is encrypted with a non-exportable Android Keystore
 * AES-GCM key. Only IV and ciphertext are stored in app-private preferences.
 * Keys are device/install-specific and are not exportable across reinstalls.
 */
object SecureMailCredentials {
    private const val STORE = "mail_secure_storage"
    private const val ALIAS = "gallery_bridge_mail_aes_gcm_v1"
    private const val KEY_EMAIL = "account"
    private const val KEY_CIPHERTEXT = "ciphertext"
    private const val KEY_IV = "iv"

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    fun isSaved(context: Context, account: String): Boolean {
        val p = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        return account.isNotBlank() && p.getString(KEY_EMAIL, "") == account.trim() &&
            !p.getString(KEY_CIPHERTEXT, "").isNullOrEmpty()
    }

    fun save(context: Context, account: String, password: String) {
        require(account.isNotBlank() && password.isNotBlank()) { "Укажи почту и пароль Mail" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(account.trim().toByteArray(Charsets.UTF_8))
        val bytes = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString(KEY_EMAIL, account.trim())
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(bytes, Base64.NO_WRAP))
            .commit()
    }

    fun load(context: Context, account: String): String? {
        if (!isSaved(context, account)) return null
        return try {
            val p = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val iv = Base64.decode(p.getString(KEY_IV, "") ?: "", Base64.NO_WRAP)
            val encrypted = Base64.decode(p.getString(KEY_CIPHERTEXT, "") ?: "", Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.updateAAD(account.trim().toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            // Old, invalid, or revoked key: do not return a corrupted password.
            clear(context)
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().clear().commit()
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        } catch (_: Exception) {
            // Removing app-private encrypted bytes is enough to disable reuse.
        }
    }
}

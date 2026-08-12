package com.schedule.njfu.data.credentials

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 学号密码经 Android Keystore AES-GCM 加密后存 SharedPreferences */
class CredentialStore(context: Context) {

    private val prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE)
    private val keyAlias = "njfu_credential_key"
    private val androidKeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun save(username: String, password: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val enc = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("username", username)
            .putString("password_iv", Base64.getEncoder().encodeToString(cipher.iv))
            .putString("password_enc", Base64.getEncoder().encodeToString(enc))
            .apply()
    }

    fun load(): Pair<String, String>? {
        val username = prefs.getString("username", null) ?: return null
        val iv = prefs.getString("password_iv", null) ?: return null
        val enc = prefs.getString("password_enc", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
            GCMParameterSpec(128, Base64.getDecoder().decode(iv)))
        val password = String(cipher.doFinal(Base64.getDecoder().decode(enc)), Charsets.UTF_8)
        return username to password
    }

    fun clear() {
        prefs.edit().clear().apply()
        androidKeyStore.deleteEntry(keyAlias)
    }

    private fun getOrCreateKey(): SecretKey {
        (androidKeyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }
}

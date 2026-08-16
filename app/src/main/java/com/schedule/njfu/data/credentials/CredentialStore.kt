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
        // 解密全流程包进 runCatching：任何一步失败（密文格式错误、Base64 解码失败、
        // AES-GCM 校验失败抛 AEADBadTagException、密钥不可用抛 IllegalArgumentException 等）
        // 都视为「无法恢复凭证」→ 返回 null，并顺带清理脏数据。
        //
        // 关键背景：Android Keystore 中的密钥【不跟随系统备份迁移】，也不在卸载重装后保留。
        // 因此从备份/换机恢复的 SharedPreferences 里，password_iv/password_enc 是用旧密钥或
        // 已丢失密钥加密的密文，解密必然失败。此时必须静默降级为未登录，而不是让异常上抛崩溃。
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                GCMParameterSpec(128, Base64.getDecoder().decode(iv)))
            val password = String(cipher.doFinal(Base64.getDecoder().decode(enc)), Charsets.UTF_8)
            username to password
        }.getOrElse {
            clear() // 密文已不可用，清掉残存的 username/iv/enc，避免反复解密失败
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
        runCatching { androidKeyStore.deleteEntry(keyAlias) }
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
